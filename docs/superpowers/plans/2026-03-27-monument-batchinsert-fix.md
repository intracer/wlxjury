# Monument batchInsert Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix `MonumentJdbc.batchInsert` to correctly update existing monuments, provide a fast clean-insert variant for the benchmark path, consolidate field-length truncation into the DAO, and cover all behaviors with tests.

**Architecture:** Three problems introduced on the `db-perf` branch: (1) `INSERT IGNORE` in `batchInsert` silently drops updates — `MonumentService.updateLists` needs upsert semantics; (2) `typ` and `subType` fields are truncated in the service but not in `batchInsert`, so direct callers (Gatling) can store over-length values; (3) truncation logic is duplicated between service and DAO. Fix: split into two methods — `batchInsert` uses `INSERT INTO … ON DUPLICATE KEY UPDATE` (more efficient than the original `REPLACE INTO`: no delete step, no auto-increment reset), `batchInsertFresh` keeps `INSERT IGNORE` for the Gatling clean-insert path where duplicates are never expected. All truncation lives in the shared `batchParams` helper. Service removes duplicate truncation.

**Tech Stack:** ScalikeJDBC 4.3, MariaDB 10.6 (Testcontainers), specs2.

---

## File Layout

```
app/db/scalikejdbc/MonumentJdbc.scala          ← Tasks 2, 3: two batchInsert variants + truncation
app/services/MonumentService.scala             ← Task 3: remove duplicated truncation
test/db/scalikejdbc/MonumentSpec.scala         ← Task 1: new test file
test/gatling/setup/GatlingDbSetup.scala        ← Task 2: call batchInsertFresh
```

---

### Task 1: MonumentSpec — failing tests for all three behaviors

**Files:**
- Create: `test/db/scalikejdbc/MonumentSpec.scala`

Each `new AutoRollbackDb` block opens a transaction and rolls it back after the test body. All helpers accept `implicit session: DBSession` so they join the same transaction and see uncommitted inserts.

The `Monument` constructor minimum fields: `id: String`, `name: String`, `page: String`. Everything else is `Option[String]` or `Option[Long]` with defaults.

- [ ] **Step 1: Create `test/db/scalikejdbc/MonumentSpec.scala`**

```scala
package db.scalikejdbc

import org.scalawiki.wlx.dto.Monument
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import scalikejdbc.DBSession

class MonumentSpec extends Specification with BeforeAll with TestDb {

  override def beforeAll(): Unit = SharedTestDb.init()

  private def monument(
      id:      String,
      name:    String         = "Monument name",
      year:    Option[String] = None,
      city:    Option[String] = None,
      typ:     Option[String] = None,
      subType: Option[String] = None,
  ): Monument = new Monument(id = id, name = name, year = year, city = city,
                              typ = typ, subType = subType, page = "Test")

  private def insert(ms: Monument*)(implicit session: DBSession): Unit =
    monumentDao.batchInsert(ms.toSeq)

  private def find(id: String)(implicit session: DBSession): Option[Monument] =
    monumentDao.find(id)

  // ── 1. Fresh insert ────────────────────────────────────────────────────────

  "batchInsert" should {

    "insert new monuments into an empty table" in new AutoRollbackDb {
      insert(monument("01-001-0001", name = "Castle"), monument("01-001-0002", name = "Bridge"))
      find("01-001-0001").map(_.name) must_== Some("Castle")
      find("01-001-0002").map(_.name) must_== Some("Bridge")
    }

  // ── 2. Upsert (ON DUPLICATE KEY UPDATE semantics) ─────────────────────────

    "overwrite an existing monument when called again with the same id" in new AutoRollbackDb {
      insert(monument("02-001-0001", name = "Old name"))
      insert(monument("02-001-0001", name = "New name"))
      find("02-001-0001").map(_.name) must_== Some("New name")
    }

    "update an optional field that was previously absent" in new AutoRollbackDb {
      insert(monument("02-002-0001", year = None))
      insert(monument("02-002-0001", year = Some("1900")))
      find("02-002-0001").flatMap(_.year) must_== Some("1900")
    }

  // ── 3. Field truncation ────────────────────────────────────────────────────

    "truncate name longer than 512 characters" in new AutoRollbackDb {
      insert(monument("03-001-0001", name = "A" * 600))
      find("03-001-0001").map(_.name.length) must_== Some(512)
    }

    "not truncate name of exactly 512 characters" in new AutoRollbackDb {
      insert(monument("03-002-0001", name = "B" * 512))
      find("03-002-0001").map(_.name.length) must_== Some(512)
    }

    "truncate year longer than 255 characters" in new AutoRollbackDb {
      insert(monument("03-003-0001", year = Some("Y" * 300)))
      find("03-003-0001").flatMap(_.year).map(_.length) must_== Some(255)
    }

    "truncate city longer than 255 characters" in new AutoRollbackDb {
      insert(monument("03-004-0001", city = Some("C" * 300)))
      find("03-004-0001").flatMap(_.city).map(_.length) must_== Some(255)
    }

    "truncate typ longer than 255 characters" in new AutoRollbackDb {
      insert(monument("03-005-0001", typ = Some("T" * 300)))
      find("03-005-0001").flatMap(_.typ).map(_.length) must_== Some(255)
    }

    "truncate subType longer than 255 characters" in new AutoRollbackDb {
      insert(monument("03-006-0001", subType = Some("S" * 300)))
      find("03-006-0001").flatMap(_.subType).map(_.length) must_== Some(255)
    }
  }
}
```

- [ ] **Step 2: Run the new tests to confirm they fail as expected**

```bash
sbt "testOnly db.scalikejdbc.MonumentSpec"
```

Expected failures under current `INSERT IGNORE` code:
- `"overwrite an existing monument"` → fails (INSERT IGNORE keeps "Old name")
- `"update an optional field that was previously absent"` → fails (INSERT IGNORE keeps `None`)
- `"truncate typ longer than 255 characters"` → fails (`typ` not truncated in batchInsert)
- `"truncate subType longer than 255 characters"` → fails (`subType` not truncated)

The fresh-insert and name/year/city truncation tests should pass already (name/year/city truncation already exists in the current `batchInsert`).

---

### Task 2: Two batchInsert variants + update GatlingDbSetup

**Files:**
- Modify: `app/db/scalikejdbc/MonumentJdbc.scala`
- Modify: `test/gatling/setup/GatlingDbSetup.scala`

**Why `INSERT … ON DUPLICATE KEY UPDATE` over `REPLACE INTO`:**
`REPLACE INTO` deletes the existing row and inserts a new one, which resets AUTO_INCREMENT sequences, fires DELETE triggers, and doubles index maintenance (delete + insert). `ON DUPLICATE KEY UPDATE` updates only the non-PK columns in place — one write per row, no delete, no index double-work.

**`ON DUPLICATE KEY UPDATE` via `InsertSQLBuilder.append`:**
`InsertSQLBuilder` (which `insertIgnore.into(…)` already returns) extends `SQLBuilder`, which has an `.append(SQLSyntax)` method that appends raw SQL after the `VALUES (?, ?, …)` clause. The `column.*` references embedded in `sqls"…"` interpolation render as bare DB column names (`column.subType` → `sub_type` after ScalikeJDBC's camelCase→snake_case conversion).

The `adm0` column is not defined in the CRUDMapper (it is inserted via `sqls"adm0" -> sqls.?`) so it is written as a literal string in the suffix.

- [ ] **Step 1: Replace the body of `MonumentJdbc.scala` from the `insertIgnore` object through the end of `batchInsert`**

Current code (lines 35–79):
```scala
  private object insertIgnore {
    def into(support: SQLSyntaxSupport[?]): InsertSQLBuilder =
      InsertSQLBuilder(sqls"insert ignore into ${support.table}")
  }

  def batchInsert(monuments: Seq[Monument])(implicit session: DBSession = AutoSession): Unit = {
    val column = MonumentJdbc.column
    val batchParams: Seq[Seq[Any]] = monuments.map(m =>
      Seq(
        m.id,
        m.name.take(512),
        m.description,
//      i.article,
        m.year.map(_.take(255)),
        m.city.map(_.take(255)),
        m.place,
        m.typ,
        m.subType,
        m.photo,
        m.gallery,
        m.page,
        m.contest,
        m.id.split("-").headOption.map(_.take(3))
      )
    )
    withSQL {
      insertIgnore
        .into(MonumentJdbc)
        .namedValues(
          column.id -> sqls.?,
          column.name -> sqls.?,
          column.description -> sqls.?,
          column.year -> sqls.?,
          column.city -> sqls.?,
          column.place -> sqls.?,
          column.typ -> sqls.?,
          column.subType -> sqls.?,
          column.photo -> sqls.?,
          column.gallery -> sqls.?,
          column.page -> sqls.?,
          column.contest -> sqls.?,
          sqls"adm0" -> sqls.?
        )
    }.batch(batchParams: _*).apply()
  }
```

Replace with:
```scala
  // ── SQL builders ──────────────────────────────────────────────────────────

  private object insertIgnore {
    def into(support: SQLSyntaxSupport[?]): InsertSQLBuilder =
      InsertSQLBuilder(sqls"insert ignore into ${support.table}")
  }

  private object insertInto {
    def into(support: SQLSyntaxSupport[?]): InsertSQLBuilder =
      InsertSQLBuilder(sqls"insert into ${support.table}")
  }

  // Appended after VALUES (…) for the upsert path.
  // column.* references render as bare DB column names (camelCase → snake_case).
  private lazy val upsertSuffix: SQLSyntax = {
    val c = MonumentJdbc.column
    sqls"""ON DUPLICATE KEY UPDATE
      ${c.name} = VALUES(${c.name}),
      ${c.description} = VALUES(${c.description}),
      ${c.year} = VALUES(${c.year}),
      ${c.city} = VALUES(${c.city}),
      ${c.place} = VALUES(${c.place}),
      ${c.typ} = VALUES(${c.typ}),
      ${c.subType} = VALUES(${c.subType}),
      ${c.photo} = VALUES(${c.photo}),
      ${c.gallery} = VALUES(${c.gallery}),
      ${c.page} = VALUES(${c.page}),
      ${c.contest} = VALUES(${c.contest}),
      adm0 = VALUES(adm0)"""
  }

  // ── Shared helpers ─────────────────────────────────────────────────────────

  private def toBatchParams(monuments: Seq[Monument]): Seq[Seq[Any]] =
    monuments.map { m =>
      Seq(
        m.id,
        m.name.take(512),
        m.description,
//      m.article,
        m.year.map(_.take(255)),
        m.city.map(_.take(255)),
        m.place,
        m.typ.map(_.take(255)),
        m.subType.map(_.take(255)),
        m.photo,
        m.gallery,
        m.page,
        m.contest,
        m.id.split("-").headOption.map(_.take(3))
      )
    }

  private def columnDefs: Seq[(SQLSyntax, SQLSyntax)] = {
    val c = MonumentJdbc.column
    Seq(
      c.id          -> sqls.?,
      c.name        -> sqls.?,
      c.description -> sqls.?,
      c.year        -> sqls.?,
      c.city        -> sqls.?,
      c.place       -> sqls.?,
      c.typ         -> sqls.?,
      c.subType     -> sqls.?,
      c.photo       -> sqls.?,
      c.gallery     -> sqls.?,
      c.page        -> sqls.?,
      c.contest     -> sqls.?,
      sqls"adm0"    -> sqls.?
    )
  }

  // ── Public API ─────────────────────────────────────────────────────────────

  /** Upsert monuments — inserts new rows, updates all columns on duplicate id.
   *  Use for production data refresh (MonumentService.updateLists).
   *  More efficient than REPLACE INTO: no delete step, no auto-increment side-effects.
   */
  def batchInsert(monuments: Seq[Monument])(implicit session: DBSession = AutoSession): Unit =
    withSQL {
      insertInto
        .into(MonumentJdbc)
        .namedValues(columnDefs: _*)
        .append(upsertSuffix)
    }.batch(toBatchParams(monuments): _*).apply()

  /** Insert monuments, ignoring rows with duplicate id.
   *  Use when the target table is known to be empty (e.g., Gatling fixture load).
   *  Avoids the update overhead of ON DUPLICATE KEY UPDATE when no conflicts exist.
   */
  def batchInsertFresh(monuments: Seq[Monument])(implicit session: DBSession = AutoSession): Unit =
    withSQL {
      insertIgnore
        .into(MonumentJdbc)
        .namedValues(columnDefs: _*)
    }.batch(toBatchParams(monuments): _*).apply()
```

- [ ] **Step 2: Update `GatlingDbSetup` to call `batchInsertFresh`**

In `test/gatling/setup/GatlingDbSetup.scala` find:

```scala
      monuments.grouped(1000).foreach(batch => MonumentJdbc.batchInsert(batch.toSeq))
```

Replace with:

```scala
      monuments.grouped(1000).foreach(batch => MonumentJdbc.batchInsertFresh(batch.toSeq))
```

- [ ] **Step 3: Compile**

```bash
sbt test:compile
```

Expected: no errors.

- [ ] **Step 4: Run MonumentSpec — all tests must now pass**

```bash
sbt "testOnly db.scalikejdbc.MonumentSpec"
```

Expected: 9 examples, 0 failures, 0 errors (all tests pass, including the previously-failing upsert and typ/subType truncation tests).

- [ ] **Step 5: Run RegionStatSpec to verify no regression**

```bash
sbt "testOnly db.scalikejdbc.RegionStatSpec"
```

Expected: 5 examples, 0 failures.

- [ ] **Step 6: Commit**

```bash
git add app/db/scalikejdbc/MonumentJdbc.scala \
        test/db/scalikejdbc/MonumentSpec.scala \
        test/gatling/setup/GatlingDbSetup.scala
git commit -m "fix: add batchInsert (ON DUPLICATE KEY UPDATE) and batchInsertFresh (INSERT IGNORE); add MonumentSpec"
```

---

### Task 3: Remove duplicated truncation from MonumentService

**Files:**
- Modify: `app/services/MonumentService.scala`

`MonumentService.updateLists` currently truncates `name`, `typ`, `subType`, `year`, and `city` via `Monument.copy()` before calling `batchInsert`. Since Task 2 moves all truncation into `toBatchParams` (used by both public methods), this logic is now redundant. Remove it.

Keep the ID-format filter (`\\d{2}-\\d{3}-\\d{4}`) — that is business logic, not a DB constraint.

- [ ] **Step 1: Remove the truncation helpers and map calls from `MonumentService.updateLists`**

Find in `app/services/MonumentService.scala`:
```scala
    def truncate(
        monument: Monument,
        field: String,
        max: Int,
        copy: String => Monument
    ): Monument = {
      if (field.length > max) {
        copy(field.substring(0, max))
      } else monument
    }

    def truncOpt(
        monument: Monument,
        field: Option[String],
        max: Int,
        copy: Option[String] => Monument
    ): Monument = {
      if (field.exists(_.length > max)) {
        copy(field.map(_.substring(0, max)))
      } else monument
    }

    val allValidMonuments = (monuments ++ specialNominationMonuments).view
      .filter(_.id.matches("\\d{2}-\\d{3}-\\d{4}"))
      .map(m => truncate(m, m.name, 512, s => m.copy(name = s)))
      .map(m => truncOpt(m, m.typ, 255, s => m.copy(typ = s)))
      .map(m => truncOpt(m, m.subType, 255, s => m.copy(subType = s)))
      .map(m => truncOpt(m, m.year, 255, s => m.copy(year = s)))
      .map(m => truncOpt(m, m.city, 255, s => m.copy(city = s)))
      .toSeq
```

Replace with:
```scala
    val allValidMonuments = (monuments ++ specialNominationMonuments)
      .filter(_.id.matches("\\d{2}-\\d{3}-\\d{4}"))
      .toSeq
```

- [ ] **Step 2: Compile**

```bash
sbt compile
```

Expected: no errors.

- [ ] **Step 3: Run MonumentSpec and RegionStatSpec**

```bash
sbt "testOnly db.scalikejdbc.MonumentSpec db.scalikejdbc.RegionStatSpec"
```

Expected: 14 examples total, 0 failures.

- [ ] **Step 4: Commit**

```bash
git add app/services/MonumentService.scala
git commit -m "refactor: remove duplicated truncation from MonumentService (batchInsert handles it)"
```

---

## Troubleshooting

**`upsertSuffix` column names**: `column.subType` renders as `sub_type` (ScalikeJDBC applies camelCase→snake_case by default). Confirm by checking `conf/db/migration/default/V6__Create_table_monument.sql` — the column is `sub_type varchar(255)`.

**`columnDefs` return type**: `Seq[(SQLSyntax, ParameterBinder)]` — `c.id -> sqls.?` uses `SQLSyntax.->[A](value: A)(implicit ev: ParameterBinderFactory[A])` which produces `(SQLSyntax, ParameterBinder)`. `namedValues` accepts `(SQLSyntax, ParameterBinder)*`, so the splat `columnDefs: _*` is correct. No explicit type annotation is needed.

**`toBatchParams` visibility**: `private` is correct since it's only used by the two `batch*` methods in the same object.

---

## Final Verification

```bash
sbt "testOnly db.scalikejdbc.MonumentSpec db.scalikejdbc.RegionStatSpec"
```

Expected: 14 examples, 0 failures, 0 errors.
