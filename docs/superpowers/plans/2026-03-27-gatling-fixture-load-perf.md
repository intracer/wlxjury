# Gatling Fixture Load Performance Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Gatling fixture data loading from 5+ minutes to under 1 minute on first run, and under 30 seconds on repeat runs against the full 38k-image / 110k-monument / 384k-selection dataset.

**Architecture:** Three independent improvements layered in order of impact. Task 1 switches `MonumentJdbc.batchInsert` from `REPLACE INTO` (delete+insert per duplicate) to `INSERT IGNORE` (skip duplicate, no delete). Task 2 wraps all bulk inserts in a single DB transaction with `unique_checks=0` and `foreign_key_checks=0`, eliminating per-row constraint overhead in InnoDB. Task 3 implements a dump-restore cache: after the first full load, `GatlingDbCache` dumps the populated DB to `data/cache/gatling-{key}.sql.gz`; on all subsequent runs it restores from this file in ~20 seconds. The cache is invalidated when migration files, CSV data, or user count change.

**Tech Stack:** MariaDB 10.6, ScalikeJDBC 4.3, Testcontainers Java 1.19.0 (`execInContainer`, `copyFileToContainer`, `copyFileFromContainer`), `mysqldump`/`mysql` CLI (present in the MariaDB Docker image).

---

## File Layout

```
app/db/scalikejdbc/MonumentJdbc.scala              ← Task 1: INSERT IGNORE
test/gatling/setup/GatlingDbSetup.scala            ← Task 2: transaction + disable checks
test/gatling/setup/GatlingDbCache.scala            ← Task 3: NEW — dump/restore cache
test/gatling/setup/GatlingTestFixture.scala        ← Task 3: use cache on init
```

---

### Task 1: Replace REPLACE INTO with INSERT IGNORE in MonumentJdbc.batchInsert

**Files:**
- Modify: `app/db/scalikejdbc/MonumentJdbc.scala`

`REPLACE INTO` works as DELETE + INSERT for each conflicting row, updating every secondary index twice. Because the Gatling test container always starts fresh, there are never any conflicts; the DELETE half of REPLACE INTO is pure overhead. `INSERT IGNORE` skips the conflict check path and is also correct for production re-loads (it silently ignores duplicate-key rows instead of replacing them, which is fine because monument data is fully reloaded from the canonical CSV anyway).

The change is to add an `insertIgnore` builder (mirroring the existing `replace` builder) and use it.

- [ ] **Step 1: Add `insertIgnore` builder and replace `replace.into` with `insertIgnore.into`**

Find in `app/db/scalikejdbc/MonumentJdbc.scala`:

```scala
  object replace {
    def into(support: SQLSyntaxSupport[?]): InsertSQLBuilder =
      InsertSQLBuilder(sqls"replace into ${support.table}")
  }
```

Replace with:

```scala
  private object insertIgnore {
    def into(support: SQLSyntaxSupport[?]): InsertSQLBuilder =
      InsertSQLBuilder(sqls"insert ignore into ${support.table}")
  }
```

Then find in `batchInsert`:

```scala
    withSQL {
      replace
        .into(MonumentJdbc)
```

Replace with:

```scala
    withSQL {
      insertIgnore
        .into(MonumentJdbc)
```

The `replace` object and its import can be removed if nothing else uses it.

- [ ] **Step 2: Verify the existing monument-related tests still compile and pass**

```bash
sbt "testOnly db.scalikejdbc.RegionStatSpec"
```

Expected: all 5 tests pass (RegionStatSpec uses `MonumentJdbc.batchInsert`).

- [ ] **Step 3: Commit**

```bash
git add app/db/scalikejdbc/MonumentJdbc.scala
git commit -m "perf: replace REPLACE INTO with INSERT IGNORE in MonumentJdbc.batchInsert"
```

---

### Task 2: Disable constraint checks + single transaction for bulk inserts in GatlingDbSetup

**Files:**
- Modify: `test/gatling/setup/GatlingDbSetup.scala`

InnoDB enforces `foreign_key_checks` and `unique_checks` per row by default. Disabling them at session level and wrapping all bulk inserts in one `DB.localTx` removes three sources of overhead:
1. Per-row FK lookup against parent tables (`selection → images`, `selection → users`, `images → monument`)
2. Unique-index check per row for every secondary index
3. Log flush on each auto-commit (eliminated by the single transaction)

Safe because all parent rows are already present when children are inserted, and the Gatling fixture CSV data has no duplicate monument IDs.

**Important — what does and does not join the transaction:**
The three `batchInsert` methods (`MonumentJdbc`, `ImageJdbc`, `SelectionJdbc`) all accept `implicit session: DBSession = AutoSession` and will use the `localTx` session when one is in scope — they benefit from the transaction and from the `SET` statements.

The entity-creation helpers (`ContestJuryJdbc.create`, the 5-argument `User.create`, `Round.create`) call through to single-arg overloads that use `autoSession` internally without an `implicit session` parameter. `Round.addUsers` also opens its own `DB.localTx` internally. These calls do **not** join the outer transaction and auto-commit independently. This is fine — they insert only a handful of rows (1 contest, 20 users, 2 rounds, 22 round_user rows) so their cost is negligible. The transaction and `SET` optimisations are only needed for the bulk inserts.

Structure the code as: entity creation first (outside transaction), then one `DB.localTx` block for the three bulk `batchInsert` loops. The `DB readOnly` block at the end (fetching regions) runs after the transaction commits, which is already the case.

- [ ] **Step 1: Add ScalikeJDBC DB import to GatlingDbSetup**

Find in `test/gatling/setup/GatlingDbSetup.scala`:

```scala
import scalikejdbc._
```

Ensure it is present (it already is; confirm before proceeding).

- [ ] **Step 2: Wrap the bulk batchInsert calls and entity creation in a single DB.localTx**

In `GatlingDbSetup.load()`, identify the block that runs from monument inserts through selection inserts. Wrap it with `DB.localTx { implicit session => ... }`, add `SET` statements at the beginning, and restore at the end.

Find the three sequential batched-insert blocks (monuments → images → selections) plus the contest/user/round creation in between, and restructure as follows:

```scala
  def load(port: Int, cfg: GatlingConfig.type): GatlingFixtureData = {
    val numUsers = cfg.users
    val fraction = cfg.jurorFraction
    val maxRate  = cfg.maxRate
    val rng      = new Random(42)

    // ── Parse CSVs (outside transaction — pure CPU) ──────────────────────────
    val monumentRows = parseCsv("data/wlm-ua-monuments.csv")
    val monuments = monumentRows.map { row =>
      new Monument(
        id   = row("id"),
        name = row("name"),
        lat  = row.get("lat"),
        lon  = row.get("lon"),
        typ  = row.get("type"),
        year = row.get("year_of_construction"),
        city = row.get("municipality"),
        page = row("id")
      )
    }

    val imageRows = parseCsv("data/wlm-UA-images-2025.csv")
    val images = imageRows.flatMap { row =>
      row.get("page_id").filter(_.nonEmpty).flatMap { pid =>
        scala.util.Try(pid.toLong).toOption.map { pageId =>
          Image(
            pageId     = pageId,
            title      = row.getOrElse("title", ""),
            url        = row.get("url").filter(_.nonEmpty),
            pageUrl    = row.get("page_url").filter(_.nonEmpty),
            width      = row.get("width").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0),
            height     = row.get("height").flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(0),
            monumentId = row.get("monument_id").filter(_.nonEmpty),
            author     = row.get("author").filter(_.nonEmpty),
            size       = row.get("size_bytes").flatMap(s => scala.util.Try(s.toInt).toOption),
            mime       = row.get("mime").filter(_.nonEmpty)
          )
        }
      }
    }
    val imagePageIds = images.map(_.pageId)

    // ── Entity creation (auto-commits individually; small row counts) ─────────
    val contest   = ContestJuryJdbc.create(id = None, name = "Gatling Perf Test Contest", year = 2024, country = "ua")
    val contestId = contest.id.get

    val jurors = (1 to numUsers).map { i =>
      val email    = s"juror$i@gatling.test"
      val password = s"pass$i"
      val user     = User.create(fullname = s"Juror $i", email = email,
                                 password = User.sha1(password), roles = Set("jury"),
                                 contestId = Some(contestId))
      (user.id.get, email, password)
    }

    val orgEmail    = "organizer@gatling.test"
    val orgPassword = "orgpass"
    val orgUser     = User.create(fullname = "Organizer", email = orgEmail,
                                  password = User.sha1(orgPassword), roles = Set("organizer"),
                                  contestId = Some(contestId))
    val organizer   = (orgUser.id.get, orgEmail, orgPassword)

    val binaryRound = Round.create(Round(id = None, number = 1, name = Some("Binary Round"),
                                         contestId = contestId, rates = Round.binaryRound, active = true))
    val ratingRound = Round.create(Round(id = None, number = 2, name = Some("Rating Round"),
                                         contestId = contestId,
                                         rates = Round.rateRounds.find(_.id == maxRate).getOrElse(Round.rateRounds.last),
                                         active = true))

    val jurorUsers = jurors.map { case (id, _, _) =>
      RoundUser(roundId = binaryRound.id.get, userId = id, role = "jury", active = true)
    }
    binaryRound.addUsers(jurorUsers)
    ratingRound.addUsers(jurorUsers)

    // ── Bulk inserts — wrapped in a single transaction with constraint checks disabled ──
    // Only batchInsert calls participate; entity creation above uses autoSession.
    val jurorIds  = jurors.map(_._1)
    val numJurors = jurorIds.length

    val binarySelections = for {
      pageId <- imagePageIds
      chosen  = rng.shuffle(jurorIds.toVector).take(math.round(numJurors * fraction).toInt)
      juryId <- chosen
    } yield Selection(pageId = pageId, juryId = juryId, roundId = binaryRound.id.get,
                      rate = if (rng.nextBoolean()) 1 else -1)

    val ratingSelections = for {
      pageId <- imagePageIds
      chosen  = rng.shuffle(jurorIds.toVector).take(math.round(numJurors * fraction).toInt)
      juryId <- chosen
    } yield Selection(pageId = pageId, juryId = juryId, roundId = ratingRound.id.get,
                      rate = rng.nextInt(maxRate) + 1)

    DB.localTx { implicit session =>
      SQL("SET foreign_key_checks = 0").execute.apply()
      SQL("SET unique_checks = 0").execute.apply()

      monuments.grouped(1000).foreach(batch => MonumentJdbc.batchInsert(batch.toSeq))
      images.grouped(1000).foreach(batch => ImageJdbc.batchInsert(batch.toSeq))
      (binarySelections ++ ratingSelections).grouped(5000).foreach(batch => SelectionJdbc.batchInsert(batch.toSeq))

      SQL("SET foreign_key_checks = 1").execute.apply()
      SQL("SET unique_checks = 1").execute.apply()
    }

    val interleavedSelections = binarySelections.zip(ratingSelections).flatMap { case (b, r) => Seq(b, r) }
    val votingPairs = interleavedSelections.map(s => (s.juryId, s.pageId, s.roundId, s.rate)).take(50000)

    // ── Regions query — runs after transaction commits ───────────────────────
    val regions = DB readOnly { implicit session =>
      sql"SELECT DISTINCT adm0 FROM monument WHERE adm0 IS NOT NULL"
        .map(_.string("adm0")).list()
    }

    GatlingFixtureData(port = port, contestId = contestId,
      roundBinaryId = binaryRound.id.get, roundRatingId = ratingRound.id.get,
      jurors = jurors, organizer = organizer,
      imagePageIds = imagePageIds, regions = regions, votingPairs = votingPairs)
  }
```

- [ ] **Step 3: Compile and run smoke test to verify the load still works**

```bash
sbt test:compile
```

Expected: no compilation errors.

Then run smoke test (which triggers a full load):

```bash
sbt "testOnly gatling.GatlinSmokeSpec"
```

Expected: all 5 scenario checks pass.

- [ ] **Step 4: Commit**

```bash
git add test/gatling/setup/GatlingDbSetup.scala
git commit -m "perf: wrap GatlingDbSetup bulk inserts in single transaction, disable FK/unique checks"
```

---

### Task 3: GatlingDbCache — dump DB after first load, restore from dump on repeat runs

**Files:**
- Create: `test/gatling/setup/GatlingDbCache.scala`
- Modify: `test/gatling/setup/GatlingTestFixture.scala`
- Modify: `test/gatling/setup/GatlingDbSetup.scala` (add `loadFromDb`)
- Modify: `.gitignore` (exclude `data/cache/`)

After `GatlingDbSetup.load()` finishes, `GatlingDbCache.save()` runs `mysqldump --no-create-info` inside the container and writes `data/cache/gatling-{key}.sql.gz` to the host. The cache key is a 16-hex-character SHA-256 prefix over migration SQL content + CSV file sizes + user count. On subsequent `GatlingTestFixture.init()` calls, if the key matches a cached file, `GatlingDbCache.restore()` copies the file into the container and pipes it through `mysql`. The Play server starts normally (Flyway detects all migrations already applied and skips them). `GatlingDbSetup.loadFromDb()` then reconstructs the `GatlingFixtureData` by querying the restored DB — no CSV parsing, no mass inserts.

**Cache invalidation:** any migration file content change, CSV file size change, or change to `GatlingConfig.users` produces a different key → cache miss → fresh full load → new dump.

#### Part A — Create GatlingDbCache

- [ ] **Step 1: Create `test/gatling/setup/GatlingDbCache.scala`**

```scala
package gatling.setup

import com.dimafeng.testcontainers.MariaDBContainer
import org.testcontainers.utility.{MountableFile, ThrowingFunction}

import java.io.{File, FileOutputStream, InputStream}
import java.nio.file.Files
import java.security.MessageDigest

/** Caches the fully-loaded Gatling fixture DB as a gzipped mysqldump.
 *
 *  Cache file: data/cache/gatling-{key}.sql.gz
 *  Cache key : SHA-256 of migration SQL content + CSV file sizes + user count (first 16 hex chars).
 *
 *  Usage in GatlingTestFixture.init():
 *    val key = GatlingDbCache.cacheKey(GatlingConfig)
 *    if (GatlingDbCache.exists(key)) GatlingDbCache.restore(container, key)
 *    else { GatlingDbSetup.load(...); GatlingDbCache.save(container, key) }
 */
object GatlingDbCache {

  private val cacheDir = new File("data/cache")

  // ── Cache key ──────────────────────────────────────────────────────────────

  def cacheKey(cfg: GatlingConfig.type): String = {
    val md = MessageDigest.getInstance("SHA-256")

    // Hash the full content of every migration SQL file (sorted by name).
    val migDir = new File("conf/db/migration/default")
    Option(migDir.listFiles()).getOrElse(Array.empty)
      .filter(_.getName.endsWith(".sql"))
      .sortBy(_.getName)
      .foreach(f => md.update(Files.readAllBytes(f.toPath)))

    // Hash CSV file sizes (large files; size change is a reliable proxy for content change).
    Seq("data/wlm-ua-monuments.csv", "data/wlm-UA-images-2025.csv").foreach { path =>
      val size = new File(path).length()
      md.update(java.nio.ByteBuffer.allocate(8).putLong(size).array())
    }

    // Hash user count (changing users produces a different fixture).
    md.update(cfg.users.toString.getBytes("UTF-8"))

    md.digest().map("%02x".format(_)).mkString.take(16)
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  def dumpFile(key: String): File = new File(cacheDir, s"gatling-$key.sql.gz")

  def exists(key: String): Boolean = dumpFile(key).exists()

  /** Restore data from cache. Call AFTER Flyway migrations (schema already exists). */
  def restore(container: MariaDBContainer, key: String): Unit = {
    val dump = dumpFile(key)
    println(s"[GatlingDbCache] Restoring from $dump …")
    container.container.copyFileToContainer(
      MountableFile.forHostPath(dump.getAbsolutePath),
      "/tmp/gatling-fixture.sql.gz"
    )
    val result = container.container.execInContainer("sh", "-c",
      s"zcat /tmp/gatling-fixture.sql.gz | mysql -u${container.username} " +
      s"-p${container.password} ${container.databaseName}"
    )
    if (result.getExitCode != 0)
      throw new RuntimeException(s"[GatlingDbCache] restore failed: ${result.getStderr}")
    println("[GatlingDbCache] Restore complete.")
  }

  /** Dump DB data (no schema) to cache file and delete stale caches. */
  def save(container: MariaDBContainer, key: String): Unit = {
    cacheDir.mkdirs()
    val dump = dumpFile(key)
    println("[GatlingDbCache] Saving dump …")
    val result = container.container.execInContainer("sh", "-c",
      s"MYSQL_PWD=${container.password} mysqldump" +
      s" -u${container.username}" +
      s" --no-create-info --single-transaction --skip-triggers --no-tablespaces" +
      s" ${container.databaseName}" +
      s" | gzip > /tmp/gatling-fixture.sql.gz"
    )
    if (result.getExitCode != 0)
      throw new RuntimeException(s"[GatlingDbCache] dump failed: ${result.getStderr}")

    // Copy the gzipped dump from the container to the host.
    container.container.copyFileFromContainer(
      "/tmp/gatling-fixture.sql.gz",
      new ThrowingFunction[InputStream, Void] {
        override def apply(stream: InputStream): Void = {
          val out = new FileOutputStream(dump)
          try stream.transferTo(out) finally out.close()
          null
        }
      }
    )

    println(s"[GatlingDbCache] Saved to $dump (${dump.length() / 1024 / 1024} MB).")

    // Remove any stale dump files for old cache keys.
    Option(cacheDir.listFiles()).getOrElse(Array.empty)
      .filter(f => f.getName.startsWith("gatling-") && f.getName.endsWith(".sql.gz") && f != dump)
      .foreach { f => f.delete(); println(s"[GatlingDbCache] Deleted stale cache: ${f.getName}") }
  }
}
```

- [ ] **Step 2: Add `data/cache/` to .gitignore**

Append to `.gitignore`:

```
data/cache/
```

#### Part B — Add GatlingDbSetup.loadFromDb

After a cache restore, the DB has all fixture data but `GatlingDbSetup.load()` must not run (it would insert duplicates). Instead, `loadFromDb()` reconstructs `GatlingFixtureData` by querying the DB.

The juror credentials are deterministic: email = `juror{i}@gatling.test`, password = `pass{i}`. The index `i` is extracted from the email. Organizer password is always `orgpass`.

- [ ] **Step 3: Add `loadFromDb` method to `GatlingDbSetup`**

Add to `test/gatling/setup/GatlingDbSetup.scala` (before the existing `private def parseCsv`):

```scala
  /** Reconstruct GatlingFixtureData from a restored DB without re-running the CSV load. */
  def loadFromDb(port: Int): GatlingFixtureData = {
    import scalikejdbc.AutoSession
    implicit val session = AutoSession

    val contest = ContestJuryJdbc.findAll()
      .find(_.name == "Gatling Perf Test Contest")
      .getOrElse(throw new RuntimeException("Gatling contest not found — is the dump stale?"))
    val contestId = contest.id.get

    val rounds = Round.findAllBy(sqls.eq(Round.defaultAlias.contestId, contestId))
    val binaryRound = rounds.find(_.name.contains("Binary"))
      .getOrElse(throw new RuntimeException("Binary round not found"))
    val ratingRound = rounds.find(_.name.contains("Rating"))
      .getOrElse(throw new RuntimeException("Rating round not found"))

    val allUsers = User.findAllBy(sqls.eq(User.defaultAlias.contestId, contestId))

    val jurors = allUsers
      .filter(_.roles.contains("jury"))
      .sortBy(_.email)
      .map { u =>
        val email = u.email.getOrElse("")
        val idx   = email.replaceAll("juror(\\d+)@.*", "$1")
        (u.id.get, email, s"pass$idx")
      }

    val orgUser = allUsers.find(_.roles.contains("organizer"))
      .getOrElse(throw new RuntimeException("Organizer not found"))
    val organizer = (orgUser.id.get, orgUser.email.getOrElse(""), "orgpass")

    val imagePageIds = sql"SELECT page_id FROM images"
      .map(_.long("page_id")).list()

    val regions = sql"SELECT DISTINCT adm0 FROM monument WHERE adm0 IS NOT NULL"
      .map(_.string("adm0")).list()

    // Split 50k evenly across both rounds so both are represented in votingPairs
    // (a bare LIMIT 50000 could return rows from only one round depending on storage order).
    val votingPairs =
      (sql"""SELECT jury_id, page_id, round_id, rate FROM selection
             WHERE round_id = ${binaryRound.id.get} LIMIT 25000"""
        .map(rs => (rs.long("jury_id"), rs.long("page_id"), rs.long("round_id"), rs.int("rate")))
        .list() ++
       sql"""SELECT jury_id, page_id, round_id, rate FROM selection
             WHERE round_id = ${ratingRound.id.get} LIMIT 25000"""
        .map(rs => (rs.long("jury_id"), rs.long("page_id"), rs.long("round_id"), rs.int("rate")))
        .list())

    GatlingFixtureData(port = port, contestId = contestId,
      roundBinaryId = binaryRound.id.get, roundRatingId = ratingRound.id.get,
      jurors = jurors, organizer = organizer,
      imagePageIds = imagePageIds, regions = regions, votingPairs = votingPairs)
  }
```

Note: `Round.defaultAlias` follows the ScalikeJDBC ORM pattern used throughout the project. Check that the field name for `contestId` matches what's in `Round` (it should be `r.contestId`).

#### Part C — Wire cache into GatlingTestFixture

- [ ] **Step 4: Modify `GatlingTestFixture.init()` to use the cache**

Find the current `init()` in `test/gatling/setup/GatlingTestFixture.scala`:

```scala
  private def init(): GatlingFixtureData = {
    val container = MariaDBContainer(
      ...
    )
    container.start()

    val port = freePort()
    val app  = new GuiceApplicationBuilder()
      .configure(Map[String, Any](
        ...
      ))
      .build()
    val server = TestServer(port, app)
    server.start()

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      try server.stop() finally container.stop()
    }))

    GatlingDbSetup.load(port, GatlingConfig)
  }
```

Replace with:

```scala
  private def init(): GatlingFixtureData = {
    val container = MariaDBContainer(
      dockerImageName = DockerImageName.parse("mariadb:10.6.22"),
      dbName          = "wlxjury",
      dbUsername      = "WLXJURY_DB_USER",
      dbPassword      = "WLXJURY_DB_PASSWORD"
    )
    container.start()

    val port = freePort()
    val app  = new GuiceApplicationBuilder()
      .configure(Map[String, Any](
        "db.default.driver"   -> container.driverClassName,
        "db.default.username" -> container.username,
        "db.default.password" -> container.password,
        "db.default.url"      -> container.jdbcUrl,
        "play.filters.disabled" -> Seq("play.filters.csrf.CSRFFilter")
      ))
      .build()
    val server = TestServer(port, app)
    server.start()    // Flyway migrations run here via flyway-play

    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      try server.stop() finally container.stop()
    }))

    val cacheKey = GatlingDbCache.cacheKey(GatlingConfig)

    if (GatlingDbCache.exists(cacheKey)) {
      GatlingDbCache.restore(container, cacheKey)
      GatlingDbSetup.loadFromDb(port)
    } else {
      val data = GatlingDbSetup.load(port, GatlingConfig)
      GatlingDbCache.save(container, cacheKey)
      data
    }
  }
```

- [ ] **Step 5: Compile**

```bash
sbt test:compile
```

Expected: no errors. Fix any import issues (add `import scalikejdbc.AutoSession` and `import scalikejdbc.sqls` as needed in `GatlingDbSetup`).

- [ ] **Step 6: First run — verifies full load + dump creation**

Delete any existing cache to force a full load:

```bash
rm -rf data/cache
sbt "testOnly gatling.GatlinSmokeSpec"
```

Expected:
- Prints `[GatlingDbCache] Saving dump …` followed by size line
- `data/cache/gatling-{key}.sql.gz` exists and is several MB
- All 5 smoke checks pass

- [ ] **Step 7: Second run — verifies cache restore**

Without deleting `data/cache`:

```bash
sbt "testOnly gatling.GatlinSmokeSpec"
```

Expected:
- Prints `[GatlingDbCache] Restoring from data/cache/gatling-{key}.sql.gz …`
- No CSV parsing or mass-insert output
- Fixture setup completes in well under 1 minute
- All 5 smoke checks pass

- [ ] **Step 8: Verify cache invalidation — change user count**

```bash
sbt -Dgatling.users=5 "testOnly gatling.GatlinSmokeSpec"
```

Expected:
- Different cache key → cache miss → full load + new dump file created
- Old dump file for the previous key is deleted

- [ ] **Step 9: Commit**

```bash
git add test/gatling/setup/GatlingDbCache.scala \
        test/gatling/setup/GatlingTestFixture.scala \
        test/gatling/setup/GatlingDbSetup.scala \
        .gitignore
git commit -m "perf: add GatlingDbCache — dump/restore fixture DB, skip full load on repeat runs"
```

---

## Troubleshooting

**`execInContainer` returns non-zero exit code for mysqldump:** Check `result.getStderr()`. A common cause is `MYSQL_PWD` not being recognised — try `-p${container.password}` directly instead. Another cause is missing `--no-tablespaces` on older MariaDB versions (already included above).

**`copyFileFromContainer` compile error:** The `ThrowingFunction` import is `org.testcontainers.utility.ThrowingFunction`. If the type parameter causes issues, cast explicitly: `new ThrowingFunction[InputStream, java.lang.Void]`.

**`Round.defaultAlias` not found in `loadFromDb`:** Use `Round.syntax("r")` instead and filter with `sqls.eq(Round.syntax("r").contestId, contestId)`.

**Flyway says "Schema version table already exists" after restore:** This is not an error — Flyway checks the `flyway_schema_history` table, which is part of the dump. All migrations appear as already applied, so Flyway skips them. This is the intended behaviour.

**`data/cache/` grows indefinitely:** The `save()` method deletes stale dump files automatically. If you see multiple files, a `save()` call was interrupted — run `rm data/cache/*.sql.gz` and re-run the smoke test.
