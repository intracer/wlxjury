# Prod-Dump Script Quality Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply four code-quality fixes to `scripts/run-with-prod-dump.sh` identified in code review: robust `--help`, password hiding, atomic dump writes, and clean exit trap behavior.

**Architecture:** All four fixes are isolated edits to a single ~170-line bash script. No new files; no structural changes. Each fix is one commit.

**Tech Stack:** bash, shellcheck.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `scripts/run-with-prod-dump.sh` | Modify (×4) | All four fixes land here |

---

### Context for all tasks

The file lives at `/Users/ilya/scala/wlxjury/scripts/run-with-prod-dump.sh`.

Key sections of the current file (line numbers for orientation):
- Lines 25-27: `--help` handler calls `sed -n '3,9p'`
- Lines 61-72: `cleanup()` function + `trap cleanup EXIT`
- Lines 83-98: `dump_prod_db()` — pipes `mysqldump` straight to `gzip > $DUMP_FILE`
- Lines 85-90: `docker run --rm ... "-p${WLXJURY_DB_PASSWORD}"` — password inline
- Lines 144-147: restore — `docker exec -i ... mysql -u root "-p${LOCAL_ROOT_PASSWORD}"`

After every task: run `bash -n scripts/run-with-prod-dump.sh && shellcheck scripts/run-with-prod-dump.sh` and confirm both pass before committing.

---

### Task 1: Replace fragile `sed` help with a `usage()` function

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

The `--help` handler currently uses `sed -n '3,9p' "${BASH_SOURCE[0]}"` which is hard-coded to line numbers. Adding or removing a line at the top of the script will silently print the wrong content.

- [ ] **Step 1: Add a `usage()` function above the argument-parsing block**

The current file has `# ── Argument parsing ─...` at line 17. Insert the `usage()` function immediately before it (after line 15 `ENV_FILE="$REPO_DIR/.env"`).

Replace this block:
```bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$REPO_DIR/.env"

# ── Argument parsing ───────────────────────────────────────────────────────────
SKIP_DUMP=false
```

With:
```bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$REPO_DIR/.env"

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
SKIP_DUMP=false
```

- [ ] **Step 2: Replace the `sed` call in the `--help` handler with `usage`**

Replace:
```bash
    --help|-h)
      sed -n '3,9p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
```

With:
```bash
    --help|-h)
      usage
      exit 0
      ;;
```

- [ ] **Step 3: Verify syntax and shellcheck**

```bash
cd /Users/ilya/scala/wlxjury
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck output.

- [ ] **Step 4: Verify `--help` output**

```bash
./scripts/run-with-prod-dump.sh --help
```

Expected:
```
Usage:
  ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
  ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
  ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
```

- [ ] **Step 5: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "fix: replace fragile sed help with usage() function"
```

---

### Task 2: Hide passwords from `ps aux` using `MYSQL_PWD` env var

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

Currently the prod password is passed as `-p${WLXJURY_DB_PASSWORD}` (inline CLI flag) on the `docker run` command, and the local root password as `-p${LOCAL_ROOT_PASSWORD}` on the `docker exec` restore command. Both are visible in `ps aux` while the process runs. The `MYSQL_PWD` environment variable is the standard way to pass a password to mysql/mysqldump without it appearing in the process list.

- [ ] **Step 1: Fix `dump_prod_db()` — pass `MYSQL_PWD` via `-e` instead of `-p` flag**

Replace:
```bash
  docker run --rm \
    mariadb:10.6 \
    mysqldump \
      -h "$WLXJURY_DB_HOST" \
      -u "$WLXJURY_DB_USER" \
      "-p${WLXJURY_DB_PASSWORD}" \
      --single-transaction \
      --quick \
      --triggers \
      --routines \
      "$WLXJURY_DB" \
  | gzip > "$DUMP_FILE"
```

With:
```bash
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
  | gzip > "$DUMP_FILE"
```

- [ ] **Step 2: Fix the restore command — pass `MYSQL_PWD` via `-e` to `docker exec`**

Replace:
```bash
zcat "$DUMP_FILE" \
  | docker exec -i "$CONTAINER_NAME" \
      mysql -u root "-p${LOCAL_ROOT_PASSWORD}" "$LOCAL_DB"
```

With:
```bash
zcat "$DUMP_FILE" \
  | docker exec -i \
      -e MYSQL_PWD="$LOCAL_ROOT_PASSWORD" \
      "$CONTAINER_NAME" \
      mysql -u root "$LOCAL_DB"
```

- [ ] **Step 3: Verify syntax and shellcheck**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck output.

- [ ] **Step 4: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "fix: pass DB passwords via MYSQL_PWD env var, not CLI flags"
```

---

### Task 3: Atomic dump writes — protect against corrupt dump on failure

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

Currently `dump_prod_db()` pipes directly into `gzip > "$DUMP_FILE"`. If `mysqldump` fails mid-stream (e.g. connection dropped), the file is truncated/corrupt but still present. A subsequent `--skip-dump` run would silently restore garbage data. Fix: write to `${DUMP_FILE}.tmp` and `mv` to the final path only on success. The global `cleanup()` trap already runs on error, so add `rm -f "${DUMP_FILE}.tmp"` there to clean up the temp file on any failure.

- [ ] **Step 1: Update `dump_prod_db()` to write to a `.tmp` file and rename on success**

Replace the body of `dump_prod_db()`:
```bash
dump_prod_db() {
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
  | gzip > "$DUMP_FILE"
  echo "    Dump written to $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"
}
```

With:
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

- [ ] **Step 2: Add temp-file cleanup to the `cleanup()` trap**

Replace:
```bash
cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    echo ""
    echo "Script exited with code $exit_code."
  fi
  echo ""
  echo "Container '$CONTAINER_NAME' may still be running on port $LOCAL_PORT."
  echo "  Inspect : docker exec -it $CONTAINER_NAME mysql -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
  echo "  Remove  : docker rm -f $CONTAINER_NAME"
}
```

With:
```bash
cleanup() {
  local exit_code=$?
  rm -f "${DUMP_FILE}.tmp" 2>/dev/null || true
  if [[ $exit_code -ne 0 ]]; then
    echo ""
    echo "Script exited with code $exit_code."
  fi
  echo ""
  echo "Container '$CONTAINER_NAME' may still be running on port $LOCAL_PORT."
  echo "  Inspect : docker exec -it $CONTAINER_NAME mysql -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
  echo "  Remove  : docker rm -f $CONTAINER_NAME"
}
```

- [ ] **Step 3: Verify syntax and shellcheck**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck output.

- [ ] **Step 4: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "fix: write dump to .tmp then rename to prevent corrupt reuse on failure"
```

---

### Task 4: Suppress redundant cleanup message on clean exit

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

The `cleanup()` EXIT trap currently always prints "Container may still be running…" — even on clean `--dump-only` exits where the script body already printed the same information. Fix: add a `CONTAINER_STARTED` flag, set it to `true` immediately after `docker run -d` in Phase 2, and only print the container hint in `cleanup()` when the exit is non-zero and the container was actually started.

- [ ] **Step 1: Add `CONTAINER_STARTED=false` to the local container config block**

The config block currently starts at line 52 (`CONTAINER_NAME=...`). Add `CONTAINER_STARTED=false` as the last line of that block.

Replace:
```bash
# ── Local container config ─────────────────────────────────────────────────────
CONTAINER_NAME="wlxjury-local-dev"
LOCAL_PORT=3307
LOCAL_DB="wlxjury"
LOCAL_USER="wlxjury_user"
LOCAL_PASSWORD="wlxjury_localpass"
LOCAL_ROOT_PASSWORD="wlxjury_rootpass"
DUMP_FILE="/tmp/wlxjury-prod.sql.gz"
```

With:
```bash
# ── Local container config ─────────────────────────────────────────────────────
CONTAINER_NAME="wlxjury-local-dev"
LOCAL_PORT=3307
LOCAL_DB="wlxjury"
LOCAL_USER="wlxjury_user"
LOCAL_PASSWORD="wlxjury_localpass"
LOCAL_ROOT_PASSWORD="wlxjury_rootpass"
DUMP_FILE="/tmp/wlxjury-prod.sql.gz"
CONTAINER_STARTED=false
```

- [ ] **Step 2: Set `CONTAINER_STARTED=true` immediately after `docker run -d` in Phase 2**

Replace:
```bash
docker run -d \
  --name "$CONTAINER_NAME" \
  -e MARIADB_DATABASE="$LOCAL_DB" \
  -e MARIADB_USER="$LOCAL_USER" \
  -e MARIADB_PASSWORD="$LOCAL_PASSWORD" \
  -e MARIADB_ROOT_PASSWORD="$LOCAL_ROOT_PASSWORD" \
  -p "${LOCAL_PORT}:3306" \
  mariadb:10.6

echo -n "    Waiting for MariaDB to accept connections"
```

With:
```bash
docker run -d \
  --name "$CONTAINER_NAME" \
  -e MARIADB_DATABASE="$LOCAL_DB" \
  -e MARIADB_USER="$LOCAL_USER" \
  -e MARIADB_PASSWORD="$LOCAL_PASSWORD" \
  -e MARIADB_ROOT_PASSWORD="$LOCAL_ROOT_PASSWORD" \
  -p "${LOCAL_PORT}:3306" \
  mariadb:10.6
CONTAINER_STARTED=true

echo -n "    Waiting for MariaDB to accept connections"
```

- [ ] **Step 3: Gate the container hint in `cleanup()` on non-zero exit AND `CONTAINER_STARTED`**

Replace:
```bash
cleanup() {
  local exit_code=$?
  rm -f "${DUMP_FILE}.tmp" 2>/dev/null || true
  if [[ $exit_code -ne 0 ]]; then
    echo ""
    echo "Script exited with code $exit_code."
  fi
  echo ""
  echo "Container '$CONTAINER_NAME' may still be running on port $LOCAL_PORT."
  echo "  Inspect : docker exec -it $CONTAINER_NAME mysql -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
  echo "  Remove  : docker rm -f $CONTAINER_NAME"
}
```

With:
```bash
cleanup() {
  local exit_code=$?
  rm -f "${DUMP_FILE}.tmp" 2>/dev/null || true
  if [[ $exit_code -ne 0 ]]; then
    echo ""
    echo "Script exited with code $exit_code."
    if [[ "$CONTAINER_STARTED" == true ]]; then
      echo ""
      echo "Container '$CONTAINER_NAME' may still be running on port $LOCAL_PORT."
      echo "  Inspect : docker exec -it $CONTAINER_NAME mysql -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
      echo "  Remove  : docker rm -f $CONTAINER_NAME"
    fi
  fi
}
```

- [ ] **Step 4: Verify syntax and shellcheck**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck output.

- [ ] **Step 5: Spot-check the `--help` flag still works (no .env needed)**

```bash
./scripts/run-with-prod-dump.sh --help
```

Expected: prints the usage block and exits 0 with no trap output.

- [ ] **Step 6: Spot-check the bad-flag path**

```bash
./scripts/run-with-prod-dump.sh --oops 2>&1; echo "exit: $?"
```

Expected output (cleanup trap fires with exit_code=1, but `CONTAINER_STARTED=false` so no container hint):
```
Unknown argument: --oops

Script exited with code 1.
exit: 1
```
The "Container may still be running" block must NOT appear.

- [ ] **Step 7: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "fix: suppress cleanup container hint on clean exit, track CONTAINER_STARTED"
```
