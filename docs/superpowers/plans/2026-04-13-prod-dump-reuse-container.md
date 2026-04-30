# Prod-Dump Script --reuse-container Flag Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `--reuse-container` flag to `scripts/run-with-prod-dump.sh` that skips the dump and restore phases when the local dev container is already running, jumping straight to `sbt run`.

**Architecture:** Add `REUSE_CONTAINER=false` variable and `--reuse-container` flag. Wrap Phases 1–3 (dump, container start, restore) in an `else` branch so the reuse path bypasses them entirely. The reuse path verifies the container is running, sets `CONTAINER_STARTED=true`, and queries DB size. Phase 4 (`sbt run`) is unchanged — it always runs at the bottom unless `--dump-only` already exited. `--reuse-container` + `--dump-only` is caught as an error.

**Tech Stack:** bash, docker CLI.

---

## File Map

| File | Action |
|---|---|
| `scripts/run-with-prod-dump.sh` | Modify — add flag, incompatibility check, reuse path, restructure phases 1–3 into else branch |

---

## Current structure (for orientation)

The script currently flows unconditionally through:
- Lines 37–54: argument parsing (`SKIP_DUMP`, `DUMP_ONLY`)
- Lines 98–104: config echo
- Lines 106–135: Phase 1 (dump)
- Lines 137–169: Phase 2 (start container)
- Lines 171–193: Phase 3 (restore + `--dump-only` exit gate)
- Lines 195–205: Phase 4 (sbt run)

---

### Task 1: Add `--reuse-container` flag and restructure phases 1–3

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

- [ ] **Step 1: Add `--reuse-container` to `usage()`**

Replace:
```bash
usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
  ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
  ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
EOF
}
```

With:
```bash
usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-with-prod-dump.sh                  # full flow: dump → restore → run
  ./scripts/run-with-prod-dump.sh --skip-dump       # re-use existing /tmp/wlxjury-prod.sql.gz
  ./scripts/run-with-prod-dump.sh --dump-only       # dump and restore, do not start sbt
  ./scripts/run-with-prod-dump.sh --reuse-container # skip dump+restore, use running container
EOF
}
```

- [ ] **Step 2: Add `REUSE_CONTAINER=false` to the variables block and `--reuse-container` to argument parsing**

Replace:
```bash
SKIP_DUMP=false
DUMP_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --skip-dump) SKIP_DUMP=true ;;
    --dump-only) DUMP_ONLY=true ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done
```

With:
```bash
SKIP_DUMP=false
DUMP_ONLY=false
REUSE_CONTAINER=false

for arg in "$@"; do
  case "$arg" in
    --skip-dump)       SKIP_DUMP=true ;;
    --dump-only)       DUMP_ONLY=true ;;
    --reuse-container) REUSE_CONTAINER=true ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done
```

- [ ] **Step 3: Add incompatibility check after arg parsing, and `Reuse container` line to the config echo**

**3a — incompatibility check:** Add it immediately after the `done` that closes the argument-parsing loop (before `.env` loading), so it fails fast without requiring `.env`.

Find:
```bash
  esac
done

# ── Load prod credentials from .env ───────────────────────────────────────────
```

Replace with:
```bash
  esac
done

if [[ "$REUSE_CONTAINER" == true && "$DUMP_ONLY" == true ]]; then
  echo "ERROR: --reuse-container and --dump-only cannot be used together." >&2
  exit 1
fi

# ── Load prod credentials from .env ───────────────────────────────────────────
```

**3b — config echo:** Replace the existing config echo block:
```bash
echo "==> Config"
echo "    Prod host  : $WLXJURY_DB_HOST"
echo "    Prod DB    : $WLXJURY_DB"
echo "    Local port : $LOCAL_PORT"
echo "    Dump file  : $DUMP_FILE"
echo "    Skip dump  : $SKIP_DUMP"
echo "    Dump only  : $DUMP_ONLY"
```

With:
```bash
echo "==> Config"
echo "    Prod host       : $WLXJURY_DB_HOST"
echo "    Prod DB         : $WLXJURY_DB"
echo "    Local port      : $LOCAL_PORT"
echo "    Dump file       : $DUMP_FILE"
echo "    Skip dump       : $SKIP_DUMP"
echo "    Dump only       : $DUMP_ONLY"
echo "    Reuse container : $REUSE_CONTAINER"
```

- [ ] **Step 4: Restructure Phases 1–3 into an `if/else` on `REUSE_CONTAINER`**

Replace everything from `# ── Phase 1: Dump` through the `--dump-only` exit gate (lines 106–193) with:

```bash
if [[ "$REUSE_CONTAINER" == true ]]; then
  # ── Reuse path ──────────────────────────────────────────────────────────────
  echo "==> Reusing existing container '$CONTAINER_NAME' on port $LOCAL_PORT (skipping dump and restore)..."
  if [[ "$(docker inspect --format='{{.State.Running}}' "$CONTAINER_NAME" 2>/dev/null)" != "true" ]]; then
    echo "ERROR: --reuse-container set but container '$CONTAINER_NAME' is not running." >&2
    echo "       Start one first:  ./scripts/run-with-prod-dump.sh --dump-only" >&2
    exit 1
  fi
  CONTAINER_STARTED=true
  reuse_db_size=$(docker exec \
    -e MYSQL_PWD="$LOCAL_ROOT_PASSWORD" \
    "$CONTAINER_NAME" \
    mysql -u root -sNe \
      "SELECT CONCAT(ROUND(SUM(data_length + index_length) / 1024 / 1024, 1), ' MB') FROM information_schema.tables WHERE table_schema = '$LOCAL_DB';" \
    2>/dev/null) || reuse_db_size="unknown"
  echo "    DB size: $reuse_db_size"

else
  # ── Phase 1: Dump ────────────────────────────────────────────────────────────
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

  if [[ "$SKIP_DUMP" == true ]]; then
    if [[ ! -f "$DUMP_FILE" ]]; then
      echo "ERROR: --skip-dump set but $DUMP_FILE does not exist." >&2
      exit 1
    fi
    echo "==> Skipping dump; using existing $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"
  else
    dump_prod_db
  fi

  # ── Phase 2: Start local MariaDB container ──────────────────────────────────
  echo "==> Starting local MariaDB container '$CONTAINER_NAME'..."

  if docker inspect "$CONTAINER_NAME" &>/dev/null; then
    echo "    Removing existing container..."
    docker rm -f "$CONTAINER_NAME"
  fi

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
  retries=60
  until docker exec "$CONTAINER_NAME" \
        mysqladmin ping -h 127.0.0.1 --silent 2>/dev/null; do
    retries=$((retries - 1))
    if [[ $retries -le 0 ]]; then
      echo ""
      echo "ERROR: MariaDB container did not become healthy in time." >&2
      docker logs "$CONTAINER_NAME" >&2
      exit 1
    fi
    echo -n "."
    sleep 1
  done
  echo " ready"

  # ── Phase 3: Restore dump ───────────────────────────────────────────────────
  echo "==> Restoring dump into local container..."
  restore_start=$SECONDS
  zcat < "$DUMP_FILE" \
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

  if [[ "$DUMP_ONLY" == true ]]; then
    echo "==> --dump-only set; skipping sbt run."
    echo "    Container '$CONTAINER_NAME' left running on port $LOCAL_PORT."
    echo "    Connect: mysql -h 127.0.0.1 -P $LOCAL_PORT -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
    exit 0
  fi

fi

# ── Phase 4: Run dev server ────────────────────────────────────────────────────
echo "==> Starting sbt dev server against local prod dump..."
echo "    DB  : jdbc:mariadb://127.0.0.1:${LOCAL_PORT}/${LOCAL_DB}"
echo "    Stop: Ctrl-C  (container '$CONTAINER_NAME' remains; remove: docker rm -f $CONTAINER_NAME)"
echo ""

WLXJURY_DB_HOST="127.0.0.1:${LOCAL_PORT}" \
WLXJURY_DB="$LOCAL_DB" \
WLXJURY_DB_USER="$LOCAL_USER" \
WLXJURY_DB_PASSWORD="$LOCAL_PASSWORD" \
  sbt run
```

**Important:** The `dump_prod_db()` function definition moves inside the `else` block in this restructuring — that is intentional and correct. Bash allows function definitions inside conditional blocks; the function is only defined (and called) when not reusing the container.

- [ ] **Step 5: Verify syntax and shellcheck**

```bash
cd /Users/ilya/scala/wlxjury
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck warnings.

- [ ] **Step 6: Smoke-test `--help`**

```bash
./scripts/run-with-prod-dump.sh --help
```

Expected output includes the `--reuse-container` line:
```
Usage:
  ./scripts/run-with-prod-dump.sh                  # full flow: dump → restore → run
  ./scripts/run-with-prod-dump.sh --skip-dump       # re-use existing /tmp/wlxjury-prod.sql.gz
  ./scripts/run-with-prod-dump.sh --dump-only       # dump and restore, do not start sbt
  ./scripts/run-with-prod-dump.sh --reuse-container # skip dump+restore, use running container
```

- [ ] **Step 7: Smoke-test incompatibility check (no .env needed — checked after arg parsing but before .env load)**

Wait — the incompatibility check is after `.env` loading. To test without `.env`, temporarily check the error path with the bad-flag test instead:

```bash
./scripts/run-with-prod-dump.sh --not-a-flag 2>&1; echo "exit: $?"
```

Expected: `Unknown argument: --not-a-flag` and `exit: 1`.

- [ ] **Step 8: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add --reuse-container flag to skip dump and restore"
```

Also update `scripts/README.md` to document the new flag:

Replace:
```markdown
**Usage:**

```bash
# Full flow: dump prod → restore locally → run sbt
./scripts/run-with-prod-dump.sh

# Re-use the cached dump from /tmp/wlxjury-prod.sql.gz (faster for repeat runs)
./scripts/run-with-prod-dump.sh --skip-dump

# Dump and restore only — leave container running for manual inspection
./scripts/run-with-prod-dump.sh --dump-only
```
```

With:
```markdown
**Usage:**

```bash
# Full flow: dump prod → restore locally → run sbt
./scripts/run-with-prod-dump.sh

# Re-use the cached dump from /tmp/wlxjury-prod.sql.gz (faster for repeat runs)
./scripts/run-with-prod-dump.sh --skip-dump

# Dump and restore only — leave container running for manual inspection
./scripts/run-with-prod-dump.sh --dump-only

# Skip dump and restore entirely — use the already-running local container
./scripts/run-with-prod-dump.sh --reuse-container
```
```

Then commit the README update separately:

```bash
git add scripts/README.md
git commit -m "docs: document --reuse-container flag in scripts/README.md"
```
