# Prod-Dump Script Stats Output Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add timing and size information to `scripts/run-with-prod-dump.sh` — dump duration, dump file size, restore duration, and restored DB size.

**Architecture:** A `format_duration()` helper converts raw seconds to `Nm Ns` strings. The dump function captures `SECONDS` before/after the docker run pipeline. The restore block (which is not inside a function) captures `SECONDS` and queries `information_schema.tables` for DB size after the restore completes. All four data points appear on existing completion lines — no new phases, no new prompts.

**Tech Stack:** bash (`SECONDS` builtin), MariaDB `information_schema.tables`.

---

## File Map

| File | Action |
|---|---|
| `scripts/run-with-prod-dump.sh` | Modify — add `format_duration()`, timing in `dump_prod_db()`, timing + DB-size query after restore |

---

## Current relevant sections (for orientation)

**Lines 17-25** — `usage()` function (insert helpers section after this)

**Lines 97-114** — `dump_prod_db()`:
```bash
dump_prod_db() {
  local tmp_file="${DUMP_FILE}.tmp"
  echo "==> Dumping prod DB (via docker mariadb:10.6 client)..."
  docker run --rm \
    -e MYSQL_PWD="$WLXJURY_DB_PASSWORD" \
    mariadb:10.6 \
    mysqldump \
      -h "$WLXJURY_DB_HOST" \
      -u "$WLXJURY_DB_USER" \
      --single-transaction \
      --quick \
      --triggers \
      --routines \
      "$WLXJURY_DB" \
  | gzip > "$tmp_file"
  mv "$tmp_file" "$DUMP_FILE"
  echo "    Dump written to $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"
}
```

**Lines 160-167** — restore block:
```bash
echo "==> Restoring dump into local container..."
zcat "$DUMP_FILE" \
  | docker exec -i \
      -e MYSQL_PWD="$LOCAL_ROOT_PASSWORD" \
      "$CONTAINER_NAME" \
      mysql -u root "$LOCAL_DB"
echo "    Restore complete."
```

---

### Task 1: Add `format_duration()` helper + dump timing

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

- [ ] **Step 1: Add a `# ── Helpers ──` section with `format_duration()` after `usage()`**

Find this block (lines 17-25):
```bash
# ── Usage ──────────────────────────────────────────────────────────────────────
usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
  ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
  ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
EOF
}

# ── Argument parsing ───────────────────────────────────────────────────────────
```

Replace with:
```bash
# ── Usage ──────────────────────────────────────────────────────────────────────
usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
  ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
  ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
EOF
}

# ── Helpers ────────────────────────────────────────────────────────────────────
format_duration() {
  local secs=$1
  if [[ $secs -lt 60 ]]; then
    echo "${secs}s"
  else
    echo "$((secs / 60))m $((secs % 60))s"
  fi
}

# ── Argument parsing ───────────────────────────────────────────────────────────
```

- [ ] **Step 2: Add timing to `dump_prod_db()`**

Replace the entire `dump_prod_db()` function:
```bash
dump_prod_db() {
  local tmp_file="${DUMP_FILE}.tmp"
  echo "==> Dumping prod DB (via docker mariadb:10.6 client)..."
  docker run --rm \
    -e MYSQL_PWD="$WLXJURY_DB_PASSWORD" \
    mariadb:10.6 \
    mysqldump \
      -h "$WLXJURY_DB_HOST" \
      -u "$WLXJURY_DB_USER" \
      --single-transaction \
      --quick \
      --triggers \
      --routines \
      "$WLXJURY_DB" \
  | gzip > "$tmp_file"
  mv "$tmp_file" "$DUMP_FILE"
  echo "    Dump written to $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"
}
```

With (add `local start=$SECONDS` at the top; update the completion echo to include size and duration):
```bash
dump_prod_db() {
  local tmp_file="${DUMP_FILE}.tmp"
  local start=$SECONDS
  echo "==> Dumping prod DB (via docker mariadb:10.6 client)..."
  docker run --rm \
    -e MYSQL_PWD="$WLXJURY_DB_PASSWORD" \
    mariadb:10.6 \
    mysqldump \
      -h "$WLXJURY_DB_HOST" \
      -u "$WLXJURY_DB_USER" \
      --single-transaction \
      --quick \
      --triggers \
      --routines \
      "$WLXJURY_DB" \
  | gzip > "$tmp_file"
  mv "$tmp_file" "$DUMP_FILE"
  echo "    Dump written to $DUMP_FILE  $(du -sh "$DUMP_FILE" | cut -f1)  (took $(format_duration "$((SECONDS - start))"))"
}
```

- [ ] **Step 3: Verify syntax and shellcheck**

```bash
cd /Users/ilya/scala/wlxjury
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck warnings.

- [ ] **Step 4: Smoke-test `format_duration` inline**

```bash
bash -c '
format_duration() {
  local secs=$1
  if [[ $secs -lt 60 ]]; then echo "${secs}s"; else echo "$((secs / 60))m $((secs % 60))s"; fi
}
format_duration 0
format_duration 45
format_duration 60
format_duration 125
'
```

Expected:
```
0s
45s
1m 0s
2m 5s
```

- [ ] **Step 5: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add dump timing and size to output"
```

---

### Task 2: Add restore timing and DB size

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

After the restore completes, query `information_schema.tables` for the total data + index size of `$LOCAL_DB`. Use `SECONDS` for elapsed time. Both figures appear on the single completion line.

- [ ] **Step 1: Replace the restore block**

Find (lines ~160-167):
```bash
echo "==> Restoring dump into local container..."
zcat "$DUMP_FILE" \
  | docker exec -i \
      -e MYSQL_PWD="$LOCAL_ROOT_PASSWORD" \
      "$CONTAINER_NAME" \
      mysql -u root "$LOCAL_DB"
echo "    Restore complete."
```

Replace with:
```bash
echo "==> Restoring dump into local container..."
restore_start=$SECONDS
zcat "$DUMP_FILE" \
  | docker exec -i \
      -e MYSQL_PWD="$LOCAL_ROOT_PASSWORD" \
      "$CONTAINER_NAME" \
      mysql -u root "$LOCAL_DB"
restore_elapsed=$((SECONDS - restore_start))
db_size=$(docker exec \
  -e MYSQL_PWD="$LOCAL_ROOT_PASSWORD" \
  "$CONTAINER_NAME" \
  mysql -u root -sNe \
    "SELECT CONCAT(ROUND(SUM(data_length + index_length) / 1024 / 1024, 1), ' MB') FROM information_schema.tables WHERE table_schema = '$LOCAL_DB';" \
  2>/dev/null) || db_size="unknown"
echo "    Restore complete  (took $(format_duration "$restore_elapsed"))  |  DB size: $db_size"
```

Notes:
- `restore_start=$SECONDS` — bash builtin integer, counts seconds since shell started; no `local` needed (not inside a function).
- `-sN` flags on `mysql`: `-s` = silent (no box-drawing), `-N` = skip column header. Output is the raw scalar value, e.g. `287.3 MB`.
- `|| db_size="unknown"` — if the query fails for any reason, the script continues and shows "unknown" rather than exiting.

- [ ] **Step 2: Verify syntax and shellcheck**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck warnings.

- [ ] **Step 3: Confirm the SQL produces a scalar**

Test the query format against a running MariaDB (can use the test container if one happens to be up, or skip if not available). The SQL to verify:

```sql
SELECT CONCAT(ROUND(SUM(data_length + index_length) / 1024 / 1024, 1), ' MB')
FROM information_schema.tables
WHERE table_schema = 'wlxjury';
```

Expected output (example): `287.3 MB` — a single value, no header row (because of `-sN`).

- [ ] **Step 4: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add restore timing and restored DB size to output"
```

---

## Expected final output appearance

```
==> Dumping prod DB (via docker mariadb:10.6 client)...
    Dump written to /tmp/wlxjury-prod.sql.gz  38M  (took 1m 47s)

==> Starting local MariaDB container 'wlxjury-local-dev'...
    Waiting for MariaDB to accept connections........ ready

==> Restoring dump into local container...
    Restore complete  (took 52s)  |  DB size: 287.3 MB
```
