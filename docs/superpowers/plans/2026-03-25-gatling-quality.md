# Gatling Quality — Follow-up Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the remaining quality issues exposed by the first Gatling baseline/optimized runs. The `jurorgallerysimulation` regressed under V45 (+56% mean, ok% dropped 69%→57%) because the ranking query in `ImageJdbc.scala` materialises the same `(jury_id, round_id)` subquery four times. This plan first adds DB-level tests that cover the exact behaviour of each method being changed, then rewrites those methods using MariaDB 10.6 window functions, fixes two remaining query-level issues from `docs/schema-performance-analysis.txt`, and finishes the schema cleanups omitted from V45.

**Rule:** Every task that modifies production code is preceded by a test-coverage task. The tests are written against the *current* implementation, run green, committed, and only then is the implementation changed. The same tests must stay green after the rewrite.

**Context:** The first perf run (`docs/gatling-comparison.txt`) showed clear wins on aggregated stats (−50% mean), region filtering (−50% p95), and round management (−30% p99). The one regression is `JurorGallerySimulation`, which exercises `byUserImageWithRatingRanked` and `byUserImageRangeRanked` in `app/db/scalikejdbc/ImageJdbc.scala`.

---

## File Layout

```
test/db/scalikejdbc/ImageRankedSpec.scala         ← NEW: tests for byUserImageWithRatingRanked + byUserImageRangeRanked
test/db/scalikejdbc/RegionStatSpec.scala          ← NEW: tests for byRegionStat (DB query, not just regions() helper)
test/db/scalikejdbc/RoundUserStatSpec.scala       ← NEW: tests for Round.roundUserStat + roundRateStat
app/db/scalikejdbc/ImageJdbc.scala                ← rewrite byUserImageWithRatingRanked + byUserImageRangeRanked
app/db/scalikejdbc/rewrite/ImageDbNew.scala       ← rewrite byRegionStat (substring → join on monument.adm0)
app/db/scalikejdbc/Round.scala                    ← rewrite roundUserStat (drive from selection, not users)
conf/db/migration/default/V46__Schema_cleanup.sql ← NEW: comment.id dup index, monument PK, idx_selection_round_rate
```

---

### Task 1: Tests for `byUserImageWithRatingRanked` and `byUserImageRangeRanked`

**Files:**
- Create: `test/db/scalikejdbc/ImageRankedSpec.scala`

Neither method is tested anywhere. Both compute rank by materialising `(jury_id, round_id)` multiple times via self-join. The tests pin the exact output contract: ordering by rank, correct rank values, correct paging, and isolation between jurors and rounds.

Pattern: each test uses `new AutoRollbackDb` (from `AutoRollbackDb`/`TestDb` traits), which provides an implicit `DBSession` and rolls back after the test. `SharedTestDb.init()` in `beforeAll` starts the MariaDB testcontainer and runs Flyway migrations once per JVM.

- [ ] **Step 1: Create `test/db/scalikejdbc/ImageRankedSpec.scala`**

```scala
package db.scalikejdbc

import org.intracer.wmua.{Image, Selection}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class ImageRankedSpec extends Specification with BeforeAll with TestDb {

  override def beforeAll(): Unit = SharedTestDb.init()

  // ── helpers ──────────────────────────────────────────────────────────────

  private def mkImage(id: Long) =
    Image(pageId = id, title = s"File:Image$id.jpg", url = None, pageUrl = None,
      width = 100, height = 100)

  private def mkSelection(pageId: Long, juryId: Long, roundId: Long, rate: Int) =
    Selection(pageId = pageId, juryId = juryId, roundId = roundId, rate = rate)

  // ── byUserImageWithRatingRanked ──────────────────────────────────────────

  "byUserImageWithRatingRanked" should {

    "return empty for no selections" in new AutoRollbackDb {
      ImageJdbc.byUserImageWithRatingRanked(userId = 1L, roundId = 1L) === Seq.empty
    }

    "rank a single image as 1" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(10L)))
      selectionDao.batchInsert(Seq(mkSelection(10L, juryId = 1L, roundId = 1L, rate = 3)))

      val result = ImageJdbc.byUserImageWithRatingRanked(userId = 1L, roundId = 1L)
      result.size === 1
      result.head.rank === Some(1)
      result.head.image.pageId === 10L
    }

    "order two images by rate descending, rank 1 is highest rate" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(20L), mkImage(21L)))
      selectionDao.batchInsert(Seq(
        mkSelection(20L, juryId = 2L, roundId = 5L, rate = 2),
        mkSelection(21L, juryId = 2L, roundId = 5L, rate = 5)
      ))

      val result = ImageJdbc.byUserImageWithRatingRanked(userId = 2L, roundId = 5L)
      result.size === 2
      result(0).rank === Some(1)
      result(0).image.pageId === 21L   // higher rate → rank 1
      result(1).rank === Some(2)
      result(1).image.pageId === 20L
    }

    "rank three images with distinct rates correctly" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(30L), mkImage(31L), mkImage(32L)))
      selectionDao.batchInsert(Seq(
        mkSelection(30L, juryId = 3L, roundId = 7L, rate = 1),
        mkSelection(31L, juryId = 3L, roundId = 7L, rate = 3),
        mkSelection(32L, juryId = 3L, roundId = 7L, rate = 2)
      ))

      val result = ImageJdbc.byUserImageWithRatingRanked(userId = 3L, roundId = 7L)
      result.size === 3
      result.map(_.rank) === Seq(Some(1), Some(2), Some(3))
      result.map(_.image.pageId) === Seq(31L, 32L, 30L)
    }

    "isolate results by juryId — other juror's selections not included" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(40L), mkImage(41L)))
      selectionDao.batchInsert(Seq(
        mkSelection(40L, juryId = 10L, roundId = 1L, rate = 5),
        mkSelection(41L, juryId = 99L, roundId = 1L, rate = 5)  // different juror
      ))

      val result = ImageJdbc.byUserImageWithRatingRanked(userId = 10L, roundId = 1L)
      result.size === 1
      result.head.image.pageId === 40L
    }

    "isolate results by roundId" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(50L), mkImage(51L)))
      selectionDao.batchInsert(Seq(
        mkSelection(50L, juryId = 5L, roundId = 2L, rate = 4),
        mkSelection(51L, juryId = 5L, roundId = 9L, rate = 4)   // different round
      ))

      val result = ImageJdbc.byUserImageWithRatingRanked(userId = 5L, roundId = 2L)
      result.size === 1
      result.head.image.pageId === 50L
    }

    "respect pageSize and offset" in new AutoRollbackDb {
      val images = (60L to 64L).map(mkImage)
      imageDao.batchInsert(images)
      selectionDao.batchInsert(images.map { img =>
        mkSelection(img.pageId, juryId = 6L, roundId = 3L, rate = (img.pageId - 60L).toInt)
      })

      // pageSize 2, offset 0 → top 2 ranked
      val page1 = ImageJdbc.byUserImageWithRatingRanked(userId = 6L, roundId = 3L, pageSize = 2, offset = 0)
      page1.size === 2
      page1.map(_.rank) === Seq(Some(1), Some(2))

      // pageSize 2, offset 2 → ranks 3 and 4
      val page2 = ImageJdbc.byUserImageWithRatingRanked(userId = 6L, roundId = 3L, pageSize = 2, offset = 2)
      page2.size === 2
      page2.map(_.rank) === Seq(Some(3), Some(4))
    }
  }

  // ── byUserImageRangeRanked ───────────────────────────────────────────────

  "byUserImageRangeRanked" should {

    "return empty for no selections" in new AutoRollbackDb {
      ImageJdbc.byUserImageRangeRanked(userId = 1L, roundId = 1L) === Seq.empty
    }

    "rank a single image as rank1=1, rank2=1" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(70L)))
      selectionDao.batchInsert(Seq(mkSelection(70L, juryId = 7L, roundId = 4L, rate = 3)))

      val result = ImageJdbc.byUserImageRangeRanked(userId = 7L, roundId = 4L)
      result.size === 1
      result.head.rank  === Some(1)
      result.head.rank2 === Some(1)
    }

    "compute rank1 and rank2 for two images" in new AutoRollbackDb {
      // rank1 = rank by rate DESC (best image is 1)
      // rank2 = rank by rate ASC  (worst image is 1)
      imageDao.batchInsert(Seq(mkImage(80L), mkImage(81L)))
      selectionDao.batchInsert(Seq(
        mkSelection(80L, juryId = 8L, roundId = 6L, rate = 3),
        mkSelection(81L, juryId = 8L, roundId = 6L, rate = 1)
      ))

      val result = ImageJdbc.byUserImageRangeRanked(userId = 8L, roundId = 6L)
      result.size === 2

      val byPageId = result.map(r => r.image.pageId -> r).toMap
      // image 80 has rate 3 (highest) → rank1=1, rank2=2
      byPageId(80L).rank  === Some(1)
      byPageId(80L).rank2 === Some(2)
      // image 81 has rate 1 (lowest) → rank1=2, rank2=1
      byPageId(81L).rank  === Some(2)
      byPageId(81L).rank2 === Some(1)
    }

    "order results by rank1 ascending" in new AutoRollbackDb {
      imageDao.batchInsert(Seq(mkImage(90L), mkImage(91L), mkImage(92L)))
      selectionDao.batchInsert(Seq(
        mkSelection(90L, juryId = 9L, roundId = 8L, rate = 1),
        mkSelection(91L, juryId = 9L, roundId = 8L, rate = 3),
        mkSelection(92L, juryId = 9L, roundId = 8L, rate = 2)
      ))

      val result = ImageJdbc.byUserImageRangeRanked(userId = 9L, roundId = 8L)
      result.map(_.image.pageId) === Seq(91L, 92L, 90L)   // 3→2→1
      result.map(_.rank)         === Seq(Some(1), Some(2), Some(3))
    }

    "respect pageSize and offset" in new AutoRollbackDb {
      val images = (100L to 104L).map(mkImage)
      imageDao.batchInsert(images)
      selectionDao.batchInsert(images.map { img =>
        mkSelection(img.pageId, juryId = 11L, roundId = 10L, rate = (img.pageId - 100L).toInt)
      })

      val page1 = ImageJdbc.byUserImageRangeRanked(userId = 11L, roundId = 10L, pageSize = 2, offset = 0)
      page1.size === 2
      page1.map(_.rank) === Seq(Some(1), Some(2))

      val page2 = ImageJdbc.byUserImageRangeRanked(userId = 11L, roundId = 10L, pageSize = 2, offset = 2)
      page2.size === 2
      page2.map(_.rank) === Seq(Some(3), Some(4))
    }
  }
}
```

- [ ] **Step 2: Run the new tests to confirm they pass against the current implementation**

```bash
sbt "testOnly db.scalikejdbc.ImageRankedSpec"
```

Expected: all tests pass (`[success]`). If any fail, fix the test (not the production code) to match actual current behaviour before continuing.

- [ ] **Step 3: Commit**

```bash
git add test/db/scalikejdbc/ImageRankedSpec.scala
git commit -m "test: add ImageRankedSpec covering byUserImageWithRatingRanked and byUserImageRangeRanked"
```

---

### Task 2: Rewrite `byUserImageWithRatingRanked` with window functions

**Files:**
- Modify: `app/db/scalikejdbc/ImageJdbc.scala`

The current query materialises `WHERE jury_id = ? AND round_id = ?` twice (as `s1` and `s2`) and uses a self-join to compute rank. MariaDB 10.6 supports `ROW_NUMBER() OVER (ORDER BY rate DESC)` which eliminates the self-join entirely and lets the engine use `idx_selection_jury_round` in a single pass.

- [ ] **Step 1: Replace `byUserImageWithRatingRanked`**

Find in `app/db/scalikejdbc/ImageJdbc.scala`:

```scala
  def byUserImageWithRatingRanked(
      userId: Long,
      roundId: Long,
      pageSize: Int = Int.MaxValue,
      offset: Int = 0
  ): Seq[ImageWithRating] =
    sql"""SELECT count(s2.page_id) + 1 AS rank, ${i.result.*}, ${s1.result.*}
    FROM images i
    JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId  AND s.round_id = $roundId) AS s1
    ON i.page_id = s1.page_id
    LEFT JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round_id = $roundId) AS s2
    ON s1.rate < s2.rate
    GROUP BY s1.page_id
    ORDER BY rank ASC
    LIMIT $pageSize
    OFFSET $offset"""
      .map(rs => (rs.int(1), ImageJdbc(i)(rs), SelectionJdbc(s1)(rs)))
      .list()
      .map { case (rank, i, s) =>
        ImageWithRating(i, Seq(s), rank = Some(rank))
      }
```

Replace with:

```scala
  def byUserImageWithRatingRanked(
      userId: Long,
      roundId: Long,
      pageSize: Int = Int.MaxValue,
      offset: Int = 0
  ): Seq[ImageWithRating] =
    sql"""SELECT ranked.rn AS rank, ${i.result.*}, ${s1.result.*}
    FROM images i
    JOIN (
      SELECT s.*, ROW_NUMBER() OVER (ORDER BY s.rate DESC) AS rn
      FROM selection s
      WHERE s.jury_id = $userId AND s.round_id = $roundId
    ) AS ranked ON i.page_id = ranked.page_id
    JOIN selection AS ${s1.table} ON ${s1.pageId} = ranked.page_id
                                 AND ${s1.juryId} = $userId
                                 AND ${s1.roundId} = $roundId
    ORDER BY rank ASC
    LIMIT $pageSize
    OFFSET $offset"""
      .map(rs => (rs.int(1), ImageJdbc(i)(rs), SelectionJdbc(s1)(rs)))
      .list()
      .map { case (rank, img, sel) =>
        ImageWithRating(img, Seq(sel), rank = Some(rank))
      }
```

- [ ] **Step 2: Run the coverage tests to verify identical behaviour**

```bash
sbt "testOnly db.scalikejdbc.ImageRankedSpec"
```

Expected: all tests pass. If any fail, fix the implementation until they do.

- [ ] **Step 3: Run the full test suite**

```bash
sbt test:compile && sbt "testOnly db.scalikejdbc.*"
```

Expected: `[success]` with no regressions.

- [ ] **Step 4: Commit**

```bash
git add app/db/scalikejdbc/ImageJdbc.scala
git commit -m "perf: rewrite byUserImageWithRatingRanked using ROW_NUMBER() window function"
```

---

### Task 3: Rewrite `byUserImageRangeRanked` with window functions

**Files:**
- Modify: `app/db/scalikejdbc/ImageJdbc.scala`

The current `byUserImageRangeRanked` materialises `(jury_id, round_id)` four times across two nested subqueries, each computing rank via self-join. Replace with a single CTE using two `ROW_NUMBER()` window functions.

- [ ] **Step 1: Replace `byUserImageRangeRanked`**

Find in `app/db/scalikejdbc/ImageJdbc.scala`:

```scala
  def byUserImageRangeRanked(
      userId: Long,
      roundId: Long,
      pageSize: Int = Int.MaxValue,
      offset: Int = 0
  ): Seq[ImageWithRating] =
    sql"""SELECT s1.rank1, s2.rank2, ${i.result.*}, ${s1.result.*}
          FROM images i JOIN
            (SELECT t1.*, count(t2.page_id) + 1 AS rank1
            FROM (SELECT * FROM selection s WHERE  s.jury_id = $userId AND s.round_id = $roundId) AS t1
            LEFT JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round_id = $roundId) AS t2
              ON  t1.rate < t2.rate
          GROUP BY t1.page_id) s1
              ON  i.page_id = s1.page_id
          JOIN
              (SELECT t1.page_id, count(t2.page_id) AS rank2
                 FROM (SELECT * FROM selection s WHERE  s.jury_id = $userId AND s.round_id = $roundId) AS t1
                 JOIN (SELECT * FROM selection s WHERE s.jury_id = $userId AND s.round_id = $roundId) AS t2
                   ON  t1.rate <= t2.rate
               GROUP BY t1.page_id) s2
            ON s1.page_id = s2.page_id
            ORDER BY rank1 ASC
          LIMIT $pageSize
          OFFSET $offset"""
      .map(rs => (rs.int(1), rs.int(2), ImageJdbc(i)(rs), SelectionJdbc(s1)(rs)))
      .list()
      .map { case (rank1, rank2, i, s) =>
        ImageWithRating(i, Seq(s), rank = Some(rank1), rank2 = Some(rank2))
      }
```

Replace with:

```scala
  def byUserImageRangeRanked(
      userId: Long,
      roundId: Long,
      pageSize: Int = Int.MaxValue,
      offset: Int = 0
  ): Seq[ImageWithRating] =
    sql"""WITH ranked AS (
            SELECT s.*,
                   ROW_NUMBER() OVER (ORDER BY s.rate DESC) AS rank1,
                   ROW_NUMBER() OVER (ORDER BY s.rate ASC)  AS rank2
            FROM selection s
            WHERE s.jury_id = $userId AND s.round_id = $roundId
          )
          SELECT ranked.rank1, ranked.rank2, ${i.result.*}, ${s1.result.*}
          FROM images i
          JOIN ranked ON i.page_id = ranked.page_id
          JOIN selection AS ${s1.table} ON ${s1.pageId} = ranked.page_id
                                       AND ${s1.juryId} = $userId
                                       AND ${s1.roundId} = $roundId
          ORDER BY rank1 ASC
          LIMIT $pageSize
          OFFSET $offset"""
      .map(rs => (rs.int(1), rs.int(2), ImageJdbc(i)(rs), SelectionJdbc(s1)(rs)))
      .list()
      .map { case (rank1, rank2, img, sel) =>
        ImageWithRating(img, Seq(sel), rank = Some(rank1), rank2 = Some(rank2))
      }
```

- [ ] **Step 2: Run the coverage tests**

```bash
sbt "testOnly db.scalikejdbc.ImageRankedSpec"
```

Expected: all tests pass.

- [ ] **Step 3: Run the full DB test suite**

```bash
sbt "testOnly db.scalikejdbc.*"
```

Expected: `[success]` with no regressions.

- [ ] **Step 4: Commit**

```bash
git add app/db/scalikejdbc/ImageJdbc.scala
git commit -m "perf: rewrite byUserImageRangeRanked using CTE + ROW_NUMBER() window functions"
```

---

### Task 4: Tests for `byRegionStat`

**Files:**
- Create: `test/db/scalikejdbc/RegionStatSpec.scala`

The existing `ImageDbNewSpec` only tests the pure `regions()` helper via a mock `Messages`. `byRegionStat` itself — the DB query — is not tested at all. It currently uses `substring(i.monument_id, 1, 2)`. These tests pin the exact set of distinct region codes it returns from a real DB.

`byRegionStat` requires `MonumentJdbc.batchInsert` to have populated the `monument` table with `adm0` derived from `m.id.split("-").headOption.map(_.take(3))`. The test must insert monuments with predictable ids so that `adm0` is known.

- [ ] **Step 1: Create `test/db/scalikejdbc/RegionStatSpec.scala`**

```scala
package db.scalikejdbc

import db.scalikejdbc.rewrite.ImageDbNew
import org.intracer.wmua.{Image, Selection}
import org.scalawiki.wlx.dto.Monument
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll
import play.api.i18n.Messages

class RegionStatSpec extends Specification with BeforeAll with Mockito with TestDb {

  override def beforeAll(): Unit = SharedTestDb.init()

  private def mkMonument(id: String): Monument =
    new Monument(id = id, name = s"Monument $id", page = id)

  private def mkImage(pageId: Long, monumentId: String): Image =
    Image(pageId = pageId, title = s"File:Image$pageId.jpg",
      url = None, pageUrl = None, width = 100, height = 100,
      monumentId = Some(monumentId))

  private def mkSelection(pageId: Long, juryId: Long, roundId: Long): Selection =
    Selection(pageId = pageId, juryId = juryId, roundId = roundId, rate = 1)

  private val messages = mock[Messages]

  "byRegionStat" should {

    "return empty when no selections exist for the round" in new AutoRollbackDb {
      val query = ImageDbNew.SelectionQuery(roundId = Some(1L))
      query.byRegionStat()(messages) === Seq.empty
    }

    "return one region for images all from the same adm0" in new AutoRollbackDb {
      // Monument id "07-123-0001" → adm0 = "07-" (first 3 chars) but
      // MonumentJdbc.batchInsert uses: m.id.split("-").headOption.map(_.take(3))
      // so "07-123-0001".split("-").head = "07", take(3) = "07"
      // adm0 stored = "07"
      MonumentJdbc.batchInsert(Seq(mkMonument("07-123-0001")))
      imageDao.batchInsert(Seq(mkImage(1L, "07-123-0001")))
      selectionDao.batchInsert(Seq(mkSelection(1L, juryId = 1L, roundId = 1L)))

      val result = ImageDbNew.SelectionQuery(roundId = Some(1L))
        .byRegionStat()(messages)

      result.map(_.id) must contain("07")
    }

    "return two distinct regions for images from different adm0 codes" in new AutoRollbackDb {
      MonumentJdbc.batchInsert(Seq(mkMonument("07-123-0001"), mkMonument("14-456-0001")))
      imageDao.batchInsert(Seq(mkImage(2L, "07-123-0001"), mkImage(3L, "14-456-0001")))
      selectionDao.batchInsert(Seq(
        mkSelection(2L, juryId = 2L, roundId = 2L),
        mkSelection(3L, juryId = 2L, roundId = 2L)
      ))

      val result = ImageDbNew.SelectionQuery(roundId = Some(2L))
        .byRegionStat()(messages)

      result.map(_.id).toSet === Set("07", "14")
    }

    "filter by userId when provided" in new AutoRollbackDb {
      MonumentJdbc.batchInsert(Seq(mkMonument("07-100-0001"), mkMonument("14-100-0001")))
      imageDao.batchInsert(Seq(mkImage(4L, "07-100-0001"), mkImage(5L, "14-100-0001")))
      selectionDao.batchInsert(Seq(
        mkSelection(4L, juryId = 3L, roundId = 3L),   // juror 3 voted on image 4 (region 07)
        mkSelection(5L, juryId = 4L, roundId = 3L)    // juror 4 voted on image 5 (region 14)
      ))

      val resultJuror3 = ImageDbNew.SelectionQuery(userId = Some(3L), roundId = Some(3L))
        .byRegionStat()(messages)
      resultJuror3.map(_.id) === Seq("07")

      val resultJuror4 = ImageDbNew.SelectionQuery(userId = Some(4L), roundId = Some(3L))
        .byRegionStat()(messages)
      resultJuror4.map(_.id) === Seq("14")
    }

    "not include regions from a different round" in new AutoRollbackDb {
      MonumentJdbc.batchInsert(Seq(mkMonument("07-200-0001"), mkMonument("14-200-0001")))
      imageDao.batchInsert(Seq(mkImage(6L, "07-200-0001"), mkImage(7L, "14-200-0001")))
      selectionDao.batchInsert(Seq(
        mkSelection(6L, juryId = 5L, roundId = 4L),
        mkSelection(7L, juryId = 5L, roundId = 9L)   // different round
      ))

      val result = ImageDbNew.SelectionQuery(roundId = Some(4L))
        .byRegionStat()(messages)
      result.map(_.id) === Seq("07")
    }
  }
}
```

- [ ] **Step 2: Run the tests against the current implementation**

```bash
sbt "testOnly db.scalikejdbc.RegionStatSpec"
```

Expected: all tests pass. If any fail due to `adm0` derivation differences, inspect `MonumentJdbc.batchInsert` (line 58: `m.id.split("-").headOption.map(_.take(3))`) and adjust the expected values in the tests to match actual behaviour — do NOT change production code yet.

- [ ] **Step 3: Commit**

```bash
git add test/db/scalikejdbc/RegionStatSpec.scala
git commit -m "test: add RegionStatSpec covering byRegionStat DB query with real monument/selection data"
```

---

### Task 5: Rewrite `byRegionStat` — join on `monument.adm0` instead of `substring(monument_id)`

**Files:**
- Modify: `app/db/scalikejdbc/rewrite/ImageDbNew.scala`

The current query uses `substring(i.monument_id, 1, 2)` which cannot use any index. `monument.adm0` stores the same prefix and is already populated by `MonumentJdbc.batchInsert`. Joining through `monument` lets MariaDB use `idx_selection_round_page` (added in V45) and the monument primary key (added in V46).

- [ ] **Step 1: Replace `byRegionStat`**

Find in `app/db/scalikejdbc/rewrite/ImageDbNew.scala`:

```scala
    def byRegionStat()(implicit messages: Messages): Seq[Region] = {
      val map = SQL(s"""select distinct substring(i.monument_id, 1, 2)
                       |from images i
                       |         join selection s on i.page_id = s.page_id
                       |              where ${userId.fold("") { id => s"s.jury_id = $id and " }}
                       |                s.round_id = ${roundId.get}""".stripMargin)
        .map(rs => rs.string(1) -> None)
        .list()
        .toMap
      regions(map, subRegions)
    }
```

Replace with:

```scala
    def byRegionStat()(implicit messages: Messages): Seq[Region] = {
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

- [ ] **Step 2: Run the coverage tests**

```bash
sbt "testOnly db.scalikejdbc.RegionStatSpec"
```

Expected: all tests pass. If the `adm0` join returns different values than `substring(monument_id, 1, 2)` did, the tests will catch the discrepancy. The source of truth is `MonumentJdbc.batchInsert` line 58 (`m.id.split("-").headOption.map(_.take(3))`), which writes the same value as `adm0` that the new join reads.

- [ ] **Step 3: Run the full DB test suite**

```bash
sbt "testOnly db.scalikejdbc.*"
```

Expected: `[success]` with no regressions.

- [ ] **Step 4: Commit**

```bash
git add app/db/scalikejdbc/rewrite/ImageDbNew.scala
git commit -m "perf: rewrite byRegionStat to join on monument.adm0 instead of substring(monument_id)"
```

---

### Task 6: Tests for `Round.roundUserStat` and `Round.roundRateStat`

**Files:**
- Create: `test/db/scalikejdbc/RoundUserStatSpec.scala`

Neither `roundUserStat` nor `roundRateStat` in `app/db/scalikejdbc/Round.scala` is tested anywhere. These tests pin the exact rows and counts returned before the join-order rewrite.

- [ ] **Step 1: Create `test/db/scalikejdbc/RoundUserStatSpec.scala`**

```scala
package db.scalikejdbc

import org.intracer.wmua.{Selection, User}
import org.specs2.mutable.Specification
import org.specs2.specification.BeforeAll

class RoundUserStatSpec extends Specification with BeforeAll with TestDb {

  override def beforeAll(): Unit = SharedTestDb.init()

  private def mkUser(email: String, contestId: Long): User =
    User(fullname = email, email = email, roles = Set("jury"), contestId = Some(contestId))

  "roundUserStat" should {

    "return empty for a round with no selections" in new AutoRollbackDb {
      Round.roundUserStat(roundId = 999L) === Seq.empty
    }

    "return one row per (juror, rate) combination" in new AutoRollbackDb {
      val contest = contestDao.create(None, "WLE", 2025, "ua")
      val round   = roundDao.create(Round(None, 1, contestId = contest.getId, rates = Round.binaryRound, active = true))
      val juror   = userDao.create(mkUser("j1@test", contest.getId))

      selectionDao.batchInsert(Seq(
        Selection(pageId = 1L, juryId = juror.getId, roundId = round.getId, rate = 1),
        Selection(pageId = 2L, juryId = juror.getId, roundId = round.getId, rate = 1),
        Selection(pageId = 3L, juryId = juror.getId, roundId = round.getId, rate = -1)
      ))

      val rows = Round.roundUserStat(round.getId)
      rows.size === 2   // two distinct rates: 1 and -1
      val byRate = rows.map(r => r.rate -> r.count).toMap
      byRate(1)  === 2
      byRate(-1) === 1
      rows.forall(_.juror == juror.getId) === true
    }

    "return rows for all jurors in the round" in new AutoRollbackDb {
      val contest = contestDao.create(None, "WLE", 2025, "ua")
      val round   = roundDao.create(Round(None, 1, contestId = contest.getId, rates = Round.binaryRound, active = true))
      val juror1  = userDao.create(mkUser("j1@test", contest.getId))
      val juror2  = userDao.create(mkUser("j2@test", contest.getId))

      selectionDao.batchInsert(Seq(
        Selection(pageId = 10L, juryId = juror1.getId, roundId = round.getId, rate = 1),
        Selection(pageId = 11L, juryId = juror2.getId, roundId = round.getId, rate = 1),
        Selection(pageId = 12L, juryId = juror2.getId, roundId = round.getId, rate = -1)
      ))

      val rows = Round.roundUserStat(round.getId)
      rows.size === 3   // juror1: (1→1), juror2: (1→1) and (-1→1)
      rows.map(_.juror).toSet === Set(juror1.getId, juror2.getId)
    }

    "not include selections from other rounds" in new AutoRollbackDb {
      val contest = contestDao.create(None, "WLE", 2025, "ua")
      val round1  = roundDao.create(Round(None, 1, contestId = contest.getId, rates = Round.binaryRound, active = true))
      val round2  = roundDao.create(Round(None, 2, contestId = contest.getId, rates = Round.binaryRound, active = false))
      val juror   = userDao.create(mkUser("j@test", contest.getId))

      selectionDao.batchInsert(Seq(
        Selection(pageId = 20L, juryId = juror.getId, roundId = round1.getId, rate = 1),
        Selection(pageId = 21L, juryId = juror.getId, roundId = round2.getId, rate = 1)
      ))

      val rows = Round.roundUserStat(round1.getId)
      rows.size === 1
      rows.head.juror === juror.getId
      rows.head.count === 1
    }
  }

  "roundRateStat" should {

    "return empty for a round with no selections" in new AutoRollbackDb {
      Round.roundRateStat(roundId = 999L) === Seq.empty
    }

    "return count of distinct images per rate" in new AutoRollbackDb {
      val contest = contestDao.create(None, "WLE", 2025, "ua")
      val round   = roundDao.create(Round(None, 1, contestId = contest.getId, rates = Round.binaryRound, active = true))
      val juror1  = userDao.create(mkUser("ja@test", contest.getId))
      val juror2  = userDao.create(mkUser("jb@test", contest.getId))

      // Image 30 is selected (rate=1) by both jurors — should count as 1 distinct image for rate=1
      // Image 31 is rejected (rate=-1) by juror1 — 1 distinct image for rate=-1
      selectionDao.batchInsert(Seq(
        Selection(pageId = 30L, juryId = juror1.getId, roundId = round.getId, rate = 1),
        Selection(pageId = 30L, juryId = juror2.getId, roundId = round.getId, rate = 1),
        Selection(pageId = 31L, juryId = juror1.getId, roundId = round.getId, rate = -1)
      ))

      val stat = Round.roundRateStat(round.getId).toMap
      stat(1)  === 1   // 1 distinct image with rate=1
      stat(-1) === 1   // 1 distinct image with rate=-1
    }
  }
}
```

- [ ] **Step 2: Run the tests against the current implementation**

```bash
sbt "testOnly db.scalikejdbc.RoundUserStatSpec"
```

Expected: all tests pass. Fix any test discrepancies against the current behaviour (not the production code) before continuing.

- [ ] **Step 3: Commit**

```bash
git add test/db/scalikejdbc/RoundUserStatSpec.scala
git commit -m "test: add RoundUserStatSpec covering roundUserStat and roundRateStat"
```

---

### Task 7: Fix `Round.roundUserStat` join order

**Files:**
- Modify: `app/db/scalikejdbc/Round.scala`

The current query drives from `users` (full table scan), then joins `selection`. The corrected query drives from `selection WHERE round_id = ?` (index scan using `idx_selection_jury_round` from V45), then joins `users`.

- [ ] **Step 1: Replace `roundUserStat`**

Find in `app/db/scalikejdbc/Round.scala`:

```scala
  def roundUserStat(roundId: Long): Seq[RoundStatRow] =
    sql"""SELECT u.id, s.rate, count(1) FROM users u
      JOIN selection s ON s.jury_id = u.id
    WHERE s.round_id = $roundId
    GROUP BY u.id, s.rate"""
      .map(rs => RoundStatRow(rs.int(1), rs.int(2), rs.int(3)))
      .list()
```

Replace with:

```scala
  def roundUserStat(roundId: Long): Seq[RoundStatRow] =
    sql"""SELECT s.jury_id, s.rate, count(1) FROM selection s
      JOIN users u ON u.id = s.jury_id
    WHERE s.round_id = $roundId
    GROUP BY s.jury_id, s.rate"""
      .map(rs => RoundStatRow(rs.int(1), rs.int(2), rs.int(3)))
      .list()
```

- [ ] **Step 2: Run the coverage tests**

```bash
sbt "testOnly db.scalikejdbc.RoundUserStatSpec"
```

Expected: all tests pass.

- [ ] **Step 3: Run the full DB test suite**

```bash
sbt "testOnly db.scalikejdbc.*"
```

Expected: `[success]` with no regressions.

- [ ] **Step 4: Commit**

```bash
git add app/db/scalikejdbc/Round.scala
git commit -m "perf: fix roundUserStat join order — drive from selection, not users"
```

---

### Task 8: V46 schema cleanup migration

**Files:**
- Create: `conf/db/migration/default/V46__Schema_cleanup.sql`

V45 deliberately omitted the schema fixes for `comment.id` redundant index, monument PRIMARY KEY, and `idx_selection_round_rate` to keep the first migration conservative. This migration adds them.

- [ ] **Step 1: Create V46__Schema_cleanup.sql**

```sql
-- Remove the redundant UNIQUE KEY on comment.id (it duplicates the PRIMARY KEY)
ALTER TABLE comment DROP INDEX id;

-- Promote monument.id to a real PRIMARY KEY (was just a regular index)
ALTER TABLE monument MODIFY id varchar(190) NOT NULL;
ALTER TABLE monument ADD PRIMARY KEY (id);
ALTER TABLE monument DROP INDEX monument_id_index;

-- Covering index for byRating queries (round_id + rate together)
CREATE INDEX idx_selection_round_rate ON selection(round_id, rate);
```

- [ ] **Step 2: Run the full test suite to verify Flyway applies V46 cleanly**

```bash
sbt "testOnly db.scalikejdbc.*"
```

Expected: Flyway applies V46 automatically on container startup; all tests pass including `RegionStatSpec` (which now benefits from the monument PRIMARY KEY for the `byRegionStat` join).

- [ ] **Step 3: Commit**

```bash
git add conf/db/migration/default/V46__Schema_cleanup.sql
git commit -m "feat: add V46 schema cleanup — monument PK, comment dup index, idx_selection_round_rate"
```

---

### Task 9: Fix `build.sbt` Gatling wiring

**Files:**
- Modify: `build.sbt`

Two issues remain from the original wiring:

1. `GatlingVersion` is `"3.8.4"` but `project/plugins.sbt` uses `gatling-sbt 4.9.6` which requires Gatling `3.11.x`. The library deps must match.
2. `Gatling / scalaSource` is commented out, so the plugin defaults to `src/test/scala` and picks up unit test files under `test/`. Uncomment and point it to `test/gatling/`.

- [ ] **Step 1: Bump GatlingVersion**

Find in `build.sbt`:

```scala
val GatlingVersion = "3.8.4"
```

Replace with:

```scala
val GatlingVersion = "3.11.5"
```

- [ ] **Step 2: Uncomment `Gatling / scalaSource`**

Find in `build.sbt`:

```scala
//      Gatling / scalaSource     := (Test / sourceDirectory).value / "gatling",
//      // Exclude Gatling simulation files from Test scope (they need gatling classpath, not Test)
//      Test / unmanagedSources / excludeFilter :=
//        HiddenFileFilter || new SimpleFileFilter(f =>
//          f.getAbsolutePath.contains("/test/gatling/simulations/")),
```

Replace with:

```scala
      Gatling / scalaSource     := (Test / sourceDirectory).value / "gatling",
```

The exclusion filter block is not needed: Gatling simulations extend `Simulation` (not a Specs2 or ScalaTest trait), so the test frameworks ignore them automatically.

- [ ] **Step 3: Fix library dependency scope**

Find in `build.sbt`:

```scala
  "io.gatling.highcharts" % "gatling-charts-highcharts" % GatlingVersion % Test,
  "io.gatling" % "gatling-test-framework" % GatlingVersion % Test
```

Replace with:

```scala
  "io.gatling.highcharts" % "gatling-charts-highcharts" % GatlingVersion % "gatling",
  "io.gatling"            % "gatling-test-framework"     % GatlingVersion % "gatling",
```

The `"gatling"` configuration is added by `GatlingPlugin` and is separate from `Test`. Using `% Test` puts the jars on the wrong classpath and causes `NoClassDefFoundError` when `sbt Gatling/test` runs.

- [ ] **Step 4: Verify compilation**

```bash
sbt Gatling/compile
```

Expected: `[success]` with no missing class errors.

- [ ] **Step 5: Commit**

```bash
git add build.sbt
git commit -m "build: bump GatlingVersion to 3.11.5, fix scalaSource and dependency scope"
```

---

### Task 10: Re-run GatlingCompare and verify JurorGallery regression is fixed

**Files:**
- No new files created. Output written to `docs/gatling-comparison.txt` by `GatlingCompare`.

- [ ] **Step 1: Ensure Docker is running**

```bash
docker info
```

Expected: Docker daemon info without error. If not: `open -a Docker && sleep 15`

- [ ] **Step 2: Run GatlingCompare**

`GatlingCompare` stashes V45, runs a baseline pass (without performance indexes), restores V45 (so V45 + V46 both apply), then runs the optimized pass and writes a side-by-side report.

```bash
sbt "Test/runMain gatling.setup.GatlingCompare" 2>&1 | tee /tmp/gatling-quality.log
```

Expected results after window-function rewrites:

- `jurorgallerysimulation` mean latency drops ≥ 30% optimized vs baseline (regression gone)
- `aggregatedratingssimulation` retains ≥ 40% mean improvement
- `roundmanagementsimulation` retains ≥ 20% p95 improvement
- `votingsimulation` ok% remains 100% both passes
- Report written to `docs/gatling-comparison.txt`

- [ ] **Step 3: Commit the updated report**

```bash
git add docs/gatling-comparison.txt
git commit -m "docs: update Gatling comparison report after window-function rewrites + V46"
```

---

---

### Task 11: Fast iteration mode (`-Dgatling.quick=true`)

**Problem:** Two-pass GatlingCompare takes 12–22 minutes per run (users=20, 70s per sim). Per-index attribution (8 passes) would take 90+ minutes. Fix: add a `quick` flag that drops to users=3, ramp=3s, duration=10s — ~15s per sim, ~2 min per full pass.

**Files:**
- Modify: `test/resources/gatling-perf.conf`
- Modify: `test/gatling/setup/GatlingConfig.scala`

- [ ] **Step 1: Add `quick` defaults to `gatling-perf.conf`**

Append to `test/resources/gatling-perf.conf`:
```hocon
gatling {
  quick = false
  quickUsers           = 3
  quickRampUpSeconds   = 3
  quickDurationSeconds = 10
}
```

- [ ] **Step 2: Read the `quick` flag in `GatlingConfig.scala`**

Replace the body of `GatlingConfig.scala` (`test/gatling/setup/GatlingConfig.scala`) so that when `gatling.quick=true` the low-traffic values are used:

```scala
package gatling.setup

import com.typesafe.config.ConfigFactory
import java.io.File

object GatlingConfig {
  private val cfg = ConfigFactory.systemProperties()
    .withFallback(ConfigFactory.parseFile(new File("test/resources/gatling-perf.conf")))
    .resolve()

  private val quick = cfg.getBoolean("gatling.quick")

  val users: Int           = if (quick) cfg.getInt("gatling.quickUsers")           else cfg.getInt("gatling.users")
  val rampUpSeconds: Int   = if (quick) cfg.getInt("gatling.quickRampUpSeconds")   else cfg.getInt("gatling.rampUpSeconds")
  val durationSeconds: Int = if (quick) cfg.getInt("gatling.quickDurationSeconds") else cfg.getInt("gatling.durationSeconds")
  val jurorFraction: Double = cfg.getDouble("gatling.jurorFraction")
  val maxRate: Int          = cfg.getInt("gatling.maxRate")
}
```

- [ ] **Step 3: Verify with a quick compile and short test run**

```bash
sbt "Test/compile"
sbt -Dgatling.quick=true "Gatling/testOnly gatling.simulations.VotingSimulation"
```

Expected: simulation completes in ~15s (3 users, 3s ramp, 10s duration).

- [ ] **Step 4: Commit**

```bash
git add test/resources/gatling-perf.conf test/gatling/setup/GatlingConfig.scala
git commit -m "feat: add gatling.quick flag for fast iteration (3 users, 10s duration)"
```

---

### Task 12: Fix CSRF errors — explicitly disable filter in GatlingTestFixture

**Problem:** `GatlingTestFixture` builds the app with `GuiceApplicationBuilder.configure(Map(...))` passing only DB connection config. The test `application.conf` has `play.filters.disabled += "play.filters.csrf.CSRFFilter"` but `GuiceApplicationBuilder` may not pick up `-Dconfig.file` from `Gatling/javaOptions` because it loads config via `play.api.Configuration` independently. The CSRF filter firing on POST requests produces server-side error log lines.

**Files:**
- Modify: `test/gatling/setup/GatlingTestFixture.scala`

- [ ] **Step 1: Add explicit CSRF disable to the configure map**

In `GatlingTestFixture.scala`, extend the configure map:

```scala
val app = new GuiceApplicationBuilder()
  .configure(Map[String, Any](
    "db.default.driver"   -> container.driverClassName,
    "db.default.username" -> container.username,
    "db.default.password" -> container.password,
    "db.default.url"      -> container.jdbcUrl,
    // Disable CSRF filter so POST simulations don't need to supply tokens.
    // play.filters.disabled is a list; the string value is parsed by Play as HOCON.
    "play.filters.disabled" -> Seq("play.filters.csrf.CSRFFilter")
  ))
  .build()
```

- [ ] **Step 2: Run a quick voting simulation and verify no CSRF lines in server output**

```bash
sbt -Dgatling.quick=true "Gatling/testOnly gatling.simulations.VotingSimulation" 2>&1 | grep -i csrf
```

Expected: no lines containing "csrf" (case-insensitive).

- [ ] **Step 3: Commit**

```bash
git add test/gatling/setup/GatlingTestFixture.scala
git commit -m "fix: explicitly disable CSRF filter in GatlingTestFixture app config"
```

---

### Task 13: Fix organiser login redirect → HTTP 500 in organiser simulations

**Problem:** After `POST /auth` the organiser is redirected (303) to `/roundstat`. Gatling auto-follows the redirect and logs it as "login organizer Redirect 1". That redirect destination returns 500 (exception in `currentRoundStat` or `roundStat` controller). Because 500 ∉ (200, 303) the login step is KO. All subsequent requests in the scenario run on an unauthenticated session and also fail — this is the primary cause of the 8% ok rate in `AggregatedRatingsSimulation` and the high KO rate in `RoundManagementSimulation`.

The fix is to stop Gatling at the 303 response with `.disableFollowRedirect`. The Play session cookie is set in the 303 headers — redirect following is not needed for authentication to succeed. The simulation then navigates directly to the desired endpoint in the next request.

**Files:**
- Modify: `test/gatling/simulations/AggregatedRatingsSimulation.scala`
- Modify: `test/gatling/simulations/RoundManagementSimulation.scala`

- [ ] **Step 1: Fix `AggregatedRatingsSimulation`**

Replace the login `exec` block:

```scala
// Before:
.exec(http("login organizer")
  .post("/auth")
  .formParam("login", orgEmail)
  .formParam("password", orgPassword)
  .check(status.in(200, 303)))

// After:
.exec(http("login organizer")
  .post("/auth")
  .disableFollowRedirect
  .formParam("login", orgEmail)
  .formParam("password", orgPassword)
  .check(status.is(303)))
```

- [ ] **Step 2: Fix `RoundManagementSimulation`**

Same change — replace the login exec:

```scala
// Before:
.exec(http("login organizer")
  .post("/auth")
  .formParam("login", orgEmail)
  .formParam("password", orgPassword)
  .check(status.in(200, 303)))

// After:
.exec(http("login organizer")
  .post("/auth")
  .disableFollowRedirect
  .formParam("login", orgEmail)
  .formParam("password", orgPassword)
  .check(status.is(303)))
```

- [ ] **Step 3: Verify ok rates improve with a quick run**

```bash
sbt -Dgatling.quick=true "Gatling/testOnly gatling.simulations.AggregatedRatingsSimulation"
sbt -Dgatling.quick=true "Gatling/testOnly gatling.simulations.RoundManagementSimulation"
```

Expected: login step ok%, and "round stats" / "rounds list" step ok% all ≥ 80%.

- [ ] **Step 4: Commit**

```bash
git add test/gatling/simulations/AggregatedRatingsSimulation.scala \
        test/gatling/simulations/RoundManagementSimulation.scala
git commit -m "fix: disable redirect following on organiser login to avoid 500 from /roundstat redirect"
```

---

### Task 14: Split V45 into per-index migrations + per-index attribution in GatlingCompare

**Problem:** V45 applies all 7 indexes at once — it's impossible to tell which index drove each simulation's change (or regression). Split into V45a–V45g (one per index) and update `GatlingCompare` to do cumulative passes.

**Files:**
- Delete: `conf/db/migration/default/V45__Add_performance_indexes.sql`
- Create: `conf/db/migration/default/V45a__idx_selection_jury_round.sql`
- Create: `conf/db/migration/default/V45b__idx_rounds_contest_active.sql`
- Create: `conf/db/migration/default/V45c__idx_round_user_round_active.sql`
- Create: `conf/db/migration/default/V45d__idx_users_contest.sql`
- Create: `conf/db/migration/default/V45e__idx_selection_page_jury_round.sql`
- Create: `conf/db/migration/default/V45f__idx_selection_round_page.sql`
- Create: `conf/db/migration/default/V45g__idx_users_wiki_account.sql`
- Modify: `test/gatling/setup/GatlingCompare.scala`

- [ ] **Step 1: Create the 7 individual migration files**

`conf/db/migration/default/V45a__idx_selection_jury_round.sql`:
```sql
-- Targets: JurorGallerySimulation (byUserImageWithRatingRanked), RegionFilterSimulation, AggregatedRatingsSimulation
CREATE INDEX idx_selection_jury_round ON selection(jury_id, round_id);
```

`conf/db/migration/default/V45b__idx_rounds_contest_active.sql`:
```sql
-- Targets: RoundManagementSimulation, JurorGallerySimulation (active round lookup)
CREATE INDEX idx_rounds_contest_active ON rounds(contest_id, active);
```

`conf/db/migration/default/V45c__idx_round_user_round_active.sql`:
```sql
-- Targets: JurorGallerySimulation (juror listing per round)
CREATE INDEX idx_round_user_round_active ON round_user(round_id, active);
```

`conf/db/migration/default/V45d__idx_users_contest.sql`:
```sql
-- Targets: RoundManagementSimulation (findByContest), AggregatedRatingsSimulation (roundUserStat)
CREATE INDEX idx_users_contest ON users(contest_id);
```

`conf/db/migration/default/V45e__idx_selection_page_jury_round.sql`:
```sql
-- Targets: VotingSimulation (per-image vote lookup: findBy, rate, destroy)
CREATE INDEX idx_selection_page_jury_round ON selection(page_id, jury_id, round_id);
```

`conf/db/migration/default/V45f__idx_selection_round_page.sql`:
```sql
-- Targets: AggregatedRatingsSimulation (roundRateStat batch query)
CREATE INDEX idx_selection_round_page ON selection(round_id, page_id);
```

`conf/db/migration/default/V45g__idx_users_wiki_account.sql`:
```sql
-- Targets: all simulations (login lookup by wiki_account)
CREATE INDEX idx_users_wiki_account ON users(wiki_account);
```

- [ ] **Step 2: Delete the original combined V45 file**

```bash
rm conf/db/migration/default/V45__Add_performance_indexes.sql
```

- [ ] **Step 3: Update `GatlingCompare` for cumulative per-index passes**

Replace the `main` method in `test/gatling/setup/GatlingCompare.scala` with a cumulative strategy. Each pass applies one additional index file (so the container receives V45a, then V45a+V45b, etc.) and records stats. The container is a fresh Testcontainer for every `sbt Gatling/test` invocation.

Add a `indexMigrations` ordered list to `GatlingCompare`:

```scala
// Ordered list of (migration filename, human label).
// GatlingCompare applies them cumulatively: pass 0 = none, pass 1 = V45a only,
// pass 2 = V45a+V45b, …, pass 7 = all.
private val indexMigrations: Seq[(String, String)] = Seq(
  "V45a__idx_selection_jury_round.sql"       -> "idx_selection_jury_round",
  "V45b__idx_rounds_contest_active.sql"      -> "idx_rounds_contest_active",
  "V45c__idx_round_user_round_active.sql"    -> "idx_round_user_round_active",
  "V45d__idx_users_contest.sql"              -> "idx_users_contest",
  "V45e__idx_selection_page_jury_round.sql"  -> "idx_selection_page_jury_round",
  "V45f__idx_selection_round_page.sql"       -> "idx_selection_round_page",
  "V45g__idx_users_wiki_account.sql"         -> "idx_users_wiki_account",
)
```

The cumulative passes work by temporarily renaming future migrations out of the directory so Flyway doesn't pick them up. In `main`:

```scala
val migrationsDir = new File(root, "conf/db/migration/default")

// Stash all V45x files (rename to .bak) before the baseline run.
def stashFrom(idx: Int): Unit =
  indexMigrations.drop(idx).foreach { case (file, _) =>
    val f = new File(migrationsDir, file)
    if (f.exists()) f.renameTo(new File(migrationsDir, file + ".bak"))
  }

def restoreUpTo(idx: Int): Unit = {
  // Restore V45a..V45(idx) from .bak; keep V45(idx+1).. as .bak
  indexMigrations.take(idx).foreach { case (file, _) =>
    val bak = new File(migrationsDir, file + ".bak")
    if (bak.exists()) bak.renameTo(new File(migrationsDir, file))
  }
}

// Pass 0: baseline — all V45x stashed
stashFrom(0)
val pass0 = runPass("baseline (no indexes)")

// Passes 1..N: add one index per pass (cumulative)
val passResults: Seq[(String, Map[String, SimStats])] =
  indexMigrations.zipWithIndex.map { case ((_, label), i) =>
    restoreUpTo(i + 1)
    val stats = runPass(s"+ $label")
    (label, stats)
  }

// Restore all files to clean state
restoreUpTo(indexMigrations.size)

// Print attribution table
printAttributionTable(pass0, passResults)
```

`printAttributionTable` outputs:

```
Index applied                   | jurorGallery | regionFilter | aggRatings | roundMgmt | voting
                                | mean   p95   | mean   p95   | mean  p95  | mean  p95 | mean
--------------------------------+--------------+--------------+------------+-----------+-------
idx_selection_jury_round        | -12%  -48%   | -30%  -50%   | -20%  -15% |  n/a  n/a | n/a
idx_rounds_contest_active       | -8%   -10%   |  n/a         |  n/a       | -21% -33% | n/a
...
```

Each cell is the delta between that pass and the immediately preceding pass (so each row shows the marginal contribution of one index).

- [ ] **Step 4: Compile and verify**

```bash
sbt "Test/compile"
```

Expected: no errors.

- [ ] **Step 5: Run quick attribution comparison**

```bash
sbt -Dgatling.quick=true "Test/runMain gatling.setup.GatlingCompare"
```

Expected: 8 passes × ~2 min = ~16 minutes total. Attribution table printed at end, report written to `docs/gatling-comparison.txt`.

- [ ] **Step 6: Commit**

```bash
git add conf/db/migration/default/V45a__idx_selection_jury_round.sql \
        conf/db/migration/default/V45b__idx_rounds_contest_active.sql \
        conf/db/migration/default/V45c__idx_round_user_round_active.sql \
        conf/db/migration/default/V45d__idx_users_contest.sql \
        conf/db/migration/default/V45e__idx_selection_page_jury_round.sql \
        conf/db/migration/default/V45f__idx_selection_round_page.sql \
        conf/db/migration/default/V45g__idx_users_wiki_account.sql \
        test/gatling/setup/GatlingCompare.scala
git rm conf/db/migration/default/V45__Add_performance_indexes.sql
git commit -m "feat: split V45 into per-index migrations V45a–V45g + cumulative attribution in GatlingCompare"
```

---

## Troubleshooting

**`CTE syntax error` on MariaDB < 10.2:** The `WITH ranked AS (...)` CTE syntax requires MariaDB 10.2+. The test container uses `mariadb:10.6.22`, so this is not an issue. If a production instance is older, upgrade MariaDB before deploying V46.

**`ROW_NUMBER() not recognised`:** Window functions require MariaDB 10.2+. Same constraint as above.

**`monument DROP INDEX monument_id_index failed`:** The index name may differ. Inspect with `SHOW INDEX FROM monument;` and adjust the `DROP INDEX` name in V46 to match.

**`ImageRankedSpec` fails on the old implementation with ties:** The old `count(s2.page_id) + 1` formula assigns the same rank to tied rates (dense rank), while `ROW_NUMBER()` assigns distinct sequential ranks. If the new tests use distinct rates for all images (as written above), both implementations produce the same result. Do not introduce tied rates in `ImageRankedSpec` — the semantics differ and the new tests are intentionally designed to be portable across both implementations.

**`RegionStatSpec` gets empty results:** Confirm that `MonumentJdbc.batchInsert` writes `adm0`. Check `SHOW COLUMNS FROM monument` to verify the `adm0` column exists (it is added in Flyway migrations applied before V45).

**`GatlingCompare` exits non-zero:** Check `/tmp/gatling-quality.log` for compilation errors. The most common cause after Task 9 is a stale `.class` file — run `sbt clean Gatling/compile` first.

**Gallery ok% still low after window function rewrite:** The `JurorGallerySimulation` also exercises the login path. If login is slow, confirm `idx_users_wiki_account` was applied by V45 and `idx_rounds_contest_active` is present (`SHOW INDEX FROM rounds`).
