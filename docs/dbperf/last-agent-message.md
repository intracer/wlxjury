Here is the full performance analysis and optimization plan for the slow list query.

---

## Root Cause Analysis

The query:
```sql
select i.page_id, ..., i.description, ..., s.created_at, s.deleted_at, ...
from images i
join selection s on i.page_id = s.page_id
where s.jury_id = 1 and s.round_id = 1
order by s.rate desc, i.monument_id asc, s.page_id asc
LIMIT 15 OFFSET 0
```

`ANALYZE FORMAT=JSON` reveals the execution plan:

1. **Outer loop: full `images` scan** — 38,000 rows read, 152 pages read (21ms)
2. **Inner lookup: 38,000 index lookups into `selection`** — `r_loops=38000` with 228,285 total page accesses, 1,184 pages read (114ms)
3. **Filesort: priority-queue sort of 38k rows** — 7ms, but only after the full nested-loop completes
4. **Total: ~169ms warm, ~1,839ms cold** (cold because 1,184 random reads each touch a different buffer page on a fresh container with a 128MB buffer pool)

The optimizer chooses `images` as the outer (driving) table because the `PRIMARY` index scan appears cheaper than 38k random lookups by its estimate. But this forces 38k random I/Os into the selection index.

---

## Optimization Plan (three complementary fixes)

### Fix 1: Denormalize `monument_id` into the `selection` table + covering index

**Why `monument_id` is the bottleneck for the sort**: The `ORDER BY` mixes `s.rate` (from selection) and `i.monument_id` (from images). No index can span both tables, so MariaDB must materialize the entire 38k-row join result into a temporary table before sorting. If `monument_id` lives in selection too, a single covering index `(jury_id, round_id, rate, monument_id, page_id)` lets the optimizer read from selection in sort order — `Using index` — then do just 15 `eq_ref` lookups into images for the selected rows.

**Migration**: Add `V48__denormalize_selection_monument_id.sql`:
```sql
ALTER TABLE selection ADD COLUMN monument_id VARCHAR(190);
UPDATE selection s JOIN images i ON s.page_id = i.page_id SET s.monument_id = i.monument_id;
CREATE INDEX idx_selection_jury_round_rate_mon_page
  ON selection(jury_id, round_id, rate, monument_id, page_id);
ANALYZE TABLE selection;
```

**New writes**: `SelectionJdbc.create()` must copy `image.monumentId` into the new column. Check the `SelectionJdbc` insert path and add the column.

**Query change in `ImageDbNew`**: The list query `join` method always starts with `from images i join selection s`. When `monument_id` is in selection, use `STRAIGHT_JOIN` with selection as the outer table and `s.monument_id` in the ORDER BY instead of `i.monument_id`. The `imagesJoinSelection` string becomes `from selection s join images i` with `STRAIGHT_JOIN`.

**Performance result** (cold cache): 159ms → 43ms (3.7x faster). On the Testcontainers container with slower I/O, the original 1,839ms should drop to ~460ms (proportional, same 3.7x ratio).

### Fix 2: Drop unused columns from the SELECT (minor secondary gain, low risk)

`i.description` (TEXT type) and `s.created_at`, `s.deleted_at` (timestamps) are fetched but never rendered in the gallery view or used in `GalleryController`/`GalleryService`. Removing them:
- For `description`: TEXT columns may be stored off-page in InnoDB, requiring additional I/O per row
- For `created_at`/`deleted_at`: minor savings in result set size

The `rowReader` in `ImageDbNew.Readers` uses `ImageJdbc(i)(rs)` which maps all declared columns from the CRUDMapper definition in `ImageJdbc`. To drop `description`, the `SelectionQuery.query()` would need to use explicit column lists instead of `${i.result.*}`. Similarly for `SelectionJdbc`. This is a more invasive change with limited benefit (~5ms warm) but reduces wire protocol overhead.

**Recommendation**: defer this — it requires bypassing the CRUDMapper `result.*` generation and the gain is small compared to Fix 1.

### Fix 3: `STRAIGHT_JOIN` without denormalization (partial fix, no schema change)

Without adding `monument_id` to selection, flipping the join order via `STRAIGHT_JOIN` (from `images i join selection s` to `selection s join images i`) still helps:
- Outer: selection — uses `idx_selection_jury_round` (38k rows, sequential index scan)
- Inner: images — 38k `eq_ref` lookups by PRIMARY KEY

`ANALYZE FORMAT=JSON` shows this takes ~130ms warm vs 169ms baseline because the selection index scan is sequential (not random), reducing page_read_count from 1,184 to ~237. However, filesort still processes all 38k rows because `monument_id` is still from images.

**Performance result** (cold cache): 159ms → ~90ms (1.8x). Worse than Fix 1 but requires no schema change.

---

## Recommended Implementation Order

1. **Fix 1 (schema + STRAIGHT_JOIN)**: Add V48 migration, update `SelectionJdbc.create()` to write `monument_id`, update `ImageDbNew.join()` to use `STRAIGHT_JOIN` + swap table order, update `orderBy` to emit `s.monument_id` instead of `i.monument_id` when in non-region mode. Expected cold improvement: ~4x.

2. **Fix 3 as fallback**: If schema change is not yet feasible, add `STRAIGHT_JOIN` alone as V48 migration is being developed — simply prepend `STRAIGHT_JOIN` to the columns string in `query()` and swap `imagesJoinSelection` to put selection first.

The subquery approach (sort selection first, then join only top-N to images) was tested and shows **26ms warm** but produces **semantically wrong results** when `monument_id` secondary sort is applied across a LIMIT boundary — it sorts by `page_id` within rate buckets (not `monument_id`) in the inner subquery, so the 15 rows joined to images will be wrong pages compared to the full sort. It is only valid if `monument_id` ordering is dropped entirely, which would change the juror's gallery page ordering.
