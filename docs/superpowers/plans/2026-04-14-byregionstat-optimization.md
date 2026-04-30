# byRegionStat Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cut `byRegionStat` from 4.9s to < 1s by replacing the 3-table `selection→images→monument` join with a 2-table `selection→monument` join that uses the V48 denormalized `monument_id` column on `selection`, backed by a new covering index.

**Architecture:** V48 already added `monument_id` to `selection` and backfilled it, so the images join in `byRegionStat` is now redundant. We add a covering index `(round_id, monument_id)` on `selection`, rewrite the query to use `s.monument_id` directly, fix the Gatling fixture to populate `monument_id` on new selections and enable the code path via `monumentIdTemplate`, and add a smoke test that verifies the gallery page (which calls `byRegionStat`) responds in < 1s.

**Tech Stack:** Scala 2.13, ScalikeJDBC 4.3, Play Framework 3.0, MariaDB 10.6, specs2, Flyway, Testcontainers

---

### Files

| Action  | Path |
|---------|------|
| Create  | `conf/db/migration/default/V50__idx_selection_round_monument_id.sql` |
| Modify  | `test/db/scalikejdbc/RegionStatSpec.scala` |
| Modify  | `app/db/scalikejdbc/rewrite/ImageDbNew.scala` (lines 115–127) |
| Modify  | `test/gatling/setup/GatlingDbSetup.scala` (lines 125–183) |
| Modify  | `test/gatling/GatlingSmokeSpec.scala` |

---

### Task 1: Add covering index migration

**Files:**
- Create: `conf/db/migration/default/V50__idx_selection_round_monument_id.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- Covering index for byRegionStat query:
--   SELECT DISTINCT m.adm0
--   FROM selection s
--   JOIN monument m ON m.id = s.monument_id
--   WHERE s.round_id = ?   [AND s.jury_id = ?]
--
-- With this index MariaDB does an index-range scan on round_id and
-- reads monument_id from the index leaf pages (no row access).
CREATE INDEX idx_selection_round_monument_id ON selection(round_id, monument_id);

ANALYZE TABLE selection;
```

- [ ] **Step 2: Verify Flyway picks it up by compiling tests**

```bash
sbt test:compile
```

Expected: compiles without errors (Flyway validates on startup; any syntax error shows up here via testcontainer).

- [ ] **Step 3: Commit**

```bash
git add conf/db/migration/default/V50__idx_selection_round_monument_id.sql
git commit -m "perf: add covering index (round_id, monument_id) on selection for byRegionStat"
```

---

### Task 2: Update RegionStatSpec to pass monumentId on selections

The current test helper creates `Selection` without `monumentId`. After the query rewrite in Task 3, selections without `monument_id` will return no results. Fix the tests first so they express the new contract, then verify they still pass with the old query.

**Files:**
- Modify: `test/db/scalikejdbc/RegionStatSpec.scala`

- [ ] **Step 1: Update the `selection` helper to accept monumentId**

In `RegionStatSpec.scala`, replace:

```scala
  private def selection(pageId: Long, userId: Long, roundId: Long): Selection =
    Selection(pageId = pageId, juryId = userId, roundId = roundId, rate = 1)
```

with:

```scala
  private def selection(pageId: Long, userId: Long, roundId: Long, monumentId: Option[String] = None): Selection =
    Selection(pageId = pageId, juryId = userId, roundId = roundId, rate = 1, monumentId = monumentId)
```

- [ ] **Step 2: Pass image monumentId in every `insertSelections` call**

Replace each `insertSelections` call in `RegionStatSpec.scala` to pass `monumentId`:

**Test "return one region code"** (roundId = 10L) — change:
```scala
      insertSelections(imgs.map(i => selection(i.pageId, userId, roundId)))
```
to:
```scala
      insertSelections(imgs.map(i => selection(i.pageId, userId, roundId, i.monumentId)))
```

**Test "return two distinct region codes"** (roundId = 20L) — same pattern:
```scala
      insertSelections(imgs.map(i => selection(i.pageId, userId, roundId, i.monumentId)))
```

**Test "filter by userId"** (roundId = 30L) — change:
```scala
      insertSelections(Seq(
        selection(imgRegion07.pageId, userId3, roundId),
        selection(imgRegion14.pageId, userId4, roundId)
      ))
```
to:
```scala
      insertSelections(Seq(
        selection(imgRegion07.pageId, userId3, roundId, imgRegion07.monumentId),
        selection(imgRegion14.pageId, userId4, roundId, imgRegion14.monumentId)
      ))
```

**Test "not include regions from a different round"** (round1 = 40L, round2 = 41L) — change:
```scala
      insertSelections(Seq(
        selection(imgRound1.pageId, userId, round1),
        selection(imgRound2.pageId, userId, round2)
      ))
```
to:
```scala
      insertSelections(Seq(
        selection(imgRound1.pageId, userId, round1, imgRound1.monumentId),
        selection(imgRound2.pageId, userId, round2, imgRound2.monumentId)
      ))
```

- [ ] **Step 3: Run RegionStatSpec — expect all tests to pass (old query still in place)**

```bash
sbt "testOnly db.scalikejdbc.RegionStatSpec"
```

Expected: all 5 examples PASS (the query still joins through images; `monument_id` on selection is populated but unused by the old query).

- [ ] **Step 4: Commit**

```bash
git add test/db/scalikejdbc/RegionStatSpec.scala
git commit -m "test: populate monumentId on selections in RegionStatSpec"
```

---

### Task 3: Rewrite byRegionStat to use s.monument_id

**Files:**
- Modify: `app/db/scalikejdbc/rewrite/ImageDbNew.scala` (lines 115–127)

- [ ] **Step 1: Replace the query in byRegionStat**

In `ImageDbNew.scala`, replace the `byRegionStat` method body:

```scala
    def byRegionStat()(implicit messages: Messages, session: DBSession = autoSession): Seq[Region] = {
      val map = SQL(s"""SELECT DISTINCT m.adm0
                       |FROM selection s
                       |JOIN images i ON i.page_id = s.page_id
                       |JOIN monument m ON m.id = i.monument_id
                       |WHERE m.adm0 IS NOT NULL
                       |  ${userId.fold("") { id => s"AND s.jury_id = $id" }}
                       |  AND s.round_id = ${roundId.get}""".stripMargin)
        .map(rs => rs.string(1) -> None)
        .list()
        .toMap
      regions(map, subRegions)
    }
```

with:

```scala
    def byRegionStat()(implicit messages: Messages, session: DBSession = autoSession): Seq[Region] = {
      val map = SQL(s"""SELECT DISTINCT m.adm0
                       |FROM selection s
                       |JOIN monument m ON m.id = s.monument_id
                       |WHERE m.adm0 IS NOT NULL
                       |  ${userId.fold("") { id => s"AND s.jury_id = $id" }}
                       |  AND s.round_id = ${roundId.get}""".stripMargin)
        .map(rs => rs.string(1) -> None)
        .list()
        .toMap
      regions(map, subRegions)
    }
```

- [ ] **Step 2: Run RegionStatSpec — all tests must still pass**

```bash
sbt "testOnly db.scalikejdbc.RegionStatSpec"
```

Expected: all 5 examples PASS (test selections now carry `monument_id`, new query uses it).

- [ ] **Step 3: Commit**

```bash
git add app/db/scalikejdbc/rewrite/ImageDbNew.scala
git commit -m "perf: rewrite byRegionStat to join selection->monument, skip images table"
```

---

### Task 4: Fix GatlingDbSetup fixture

Two changes needed in `GatlingDbSetup.load()`:
1. Pass `monumentId` when constructing `Selection` objects (currently `None`, so `monument_id = NULL` in DB)
2. Set `monumentIdTemplate` on the contest so `GalleryController` actually calls `byRegionStat`

The cache key is hashed over migration SQL content; adding V50 in Task 1 already invalidates any stale cache, so the next test run will rebuild from `load()`.

**Files:**
- Modify: `test/gatling/setup/GatlingDbSetup.scala`

- [ ] **Step 1: Build a pageId→monumentId map after parsing images**

In `GatlingDbSetup.load()`, immediately after the line:
```scala
    val imagePageIds = images.map(_.pageId)
```
add:
```scala
    val imageMonumentMap: Map[Long, Option[String]] = images.map(img => img.pageId -> img.monumentId).toMap
```

- [ ] **Step 2: Pass monumentId when constructing binarySelections**

Replace:
```scala
    val binarySelections = for {
      pageId <- imagePageIds
      chosen  = rng.shuffle(jurorIds.toVector).take(math.round(numJurors * fraction).toInt)
      juryId <- chosen
    } yield Selection(pageId = pageId, juryId = juryId, roundId = binaryRound.id.get,
                      rate = if (rng.nextBoolean()) 1 else -1)
```
with:
```scala
    val binarySelections = for {
      pageId <- imagePageIds
      chosen  = rng.shuffle(jurorIds.toVector).take(math.round(numJurors * fraction).toInt)
      juryId <- chosen
    } yield Selection(pageId = pageId, juryId = juryId, roundId = binaryRound.id.get,
                      rate = if (rng.nextBoolean()) 1 else -1,
                      monumentId = imageMonumentMap.getOrElse(pageId, None))
```

- [ ] **Step 3: Pass monumentId when constructing ratingSelections**

Replace:
```scala
    val ratingSelections = for {
      pageId <- imagePageIds
      chosen  = rng.shuffle(jurorIds.toVector).take(math.round(numJurors * fraction).toInt)
      juryId <- chosen
    } yield Selection(pageId = pageId, juryId = juryId, roundId = ratingRound.id.get,
                      rate = rng.nextInt(maxRate) + 1)
```
with:
```scala
    val ratingSelections = for {
      pageId <- imagePageIds
      chosen  = rng.shuffle(jurorIds.toVector).take(math.round(numJurors * fraction).toInt)
      juryId <- chosen
    } yield Selection(pageId = pageId, juryId = juryId, roundId = ratingRound.id.get,
                      rate = rng.nextInt(maxRate) + 1,
                      monumentId = imageMonumentMap.getOrElse(pageId, None))
```

- [ ] **Step 4: Set monumentIdTemplate on the Gatling contest**

Replace:
```scala
    val contest   = ContestJuryJdbc.create(id = None, name = "Gatling Perf Test Contest", year = 2024, country = "ua")
```
with:
```scala
    val contest   = ContestJuryJdbc.create(id = None, name = "Gatling Perf Test Contest", year = 2024, country = "ua",
                                           monumentIdTemplate = Some("{{UkrainianMonument}}"))
```

- [ ] **Step 5: Compile to verify no errors**

```bash
sbt test:compile
```

Expected: compiles without errors.

- [ ] **Step 6: Commit**

```bash
git add test/gatling/setup/GatlingDbSetup.scala
git commit -m "feat: populate selection.monument_id and set monumentIdTemplate in Gatling fixture"
```

---

### Task 5: Add RegionStat smoke test to GatlingSmokeSpec

**Files:**
- Modify: `test/gatling/GatlingSmokeSpec.scala`

- [ ] **Step 1: Add the smoke test after the existing "RegionFilter smoke" test**

In `GatlingSmokeSpec.scala`, after the `"RegionFilter smoke"` block (line 93), add:

```scala
  "RegionStat smoke" in {
    // byRegionStat is called during gallery page render when contest.monumentIdTemplate.isDefined.
    // This test verifies the SELECT DISTINCT … FROM selection JOIN monument query stays < 1s.
    val (cl, jurorId) = jurorClient()
    val r = doGet(cl, s"/gallery/round/${f.roundBinaryId}/user/$jurorId/page/1")
    r.status must_== 200
    r.ms must be_<=(MaxMs)
  }
```

- [ ] **Step 2: Run GatlingSmokeSpec — all smoke tests must pass**

```bash
sbt "testOnly gatling.GatlingSmokeSpec"
```

Expected: all 6 examples PASS including "RegionStat smoke" in < 1s. If "RegionStat smoke" fails with `ms > 1000`, the index from Task 1 or the query rewrite from Task 3 is not taking effect — verify that `monument_id` is non-null in `selection` rows (check `GatlingDbSetup` changes in Task 4) and that the new migration ran (check Flyway log output).

- [ ] **Step 3: Commit**

```bash
git add test/gatling/GatlingSmokeSpec.scala
git commit -m "test: add RegionStat smoke test verifying byRegionStat < 1s"
```

---

### Self-Review Checklist

- [x] **Migration V50** covered in Task 1
- [x] **`byRegionStat` rewrite** covered in Task 3
- [x] **`GatlingDbSetup` monument_id** covered in Task 4 steps 1–3
- [x] **`GatlingDbSetup` monumentIdTemplate** covered in Task 4 step 4
- [x] **`RegionStatSpec` monument_id** covered in Task 2
- [x] **Smoke test** covered in Task 5
- [x] Cache invalidation: adding V50 changes migration hash → old cache file stale → `load()` rebuilds with new monument_id and monumentIdTemplate
- [x] `loadFromDb()` (used on cache hit) reads contest from DB, which now has `monumentIdTemplate` set — no separate fix needed
- [x] No placeholders
