# Prod-Dump Local Dev Script Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A single script (`scripts/run-with-prod-dump.sh`) that dumps the production DB, restores it into a local MariaDB 10.6 Docker container, and starts the dev server against it.

**Architecture:** The script has four phases — dump, start container, restore, run. It uses `docker run --rm mariadb:10.6` to perform the dump (no local mysql client required). A `--skip-dump` flag allows re-using a cached dump file for faster iteration. The container is started fresh each run (old container with the same name is removed first). The sbt process is launched with local DB env vars that override anything in `.env`.

**Tech Stack:** bash, docker CLI, mariadb:10.6 image, sbt, `.env` file (already present at repo root), `shellcheck` for linting.

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `scripts/run-with-prod-dump.sh` | Create | Main script — dump, restore, run |
| `scripts/README.md` | Create | One-paragraph description of the script and its flags |

---

### Task 1: Script scaffold — argument parsing, .env loading, constants

**Files:**
- Create: `scripts/run-with-prod-dump.sh`

- [ ] **Step 1: Create the script file with scaffold**

```bash
#!/usr/bin/env bash
# scripts/run-with-prod-dump.sh
#
# Dumps the production DB, restores it into a local MariaDB 10.6 Docker
# container, and starts the sbt dev server against it.
#
# Usage:
#   ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
#   ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
#   ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$REPO_DIR/.env"

SKIP_DUMP=false
DUMP_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --skip-dump) SKIP_DUMP=true ;;
    --dump-only) DUMP_ONLY=true ;;
    --help|-h)
      sed -n '3,9p' "${BASH_SOURCE[0]}"   # print the comment header
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

# ── Load prod credentials from .env ───────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found. Create it with WLXJURY_DB_HOST, WLXJURY_DB, WLXJURY_DB_USER, WLXJURY_DB_PASSWORD." >&2
  exit 1
fi
# shellcheck source=/dev/null
set -a; source "$ENV_FILE"; set +a

: "${WLXJURY_DB_HOST:?WLXJURY_DB_HOST must be set in .env}"
: "${WLXJURY_DB:?WLXJURY_DB must be set in .env}"
: "${WLXJURY_DB_USER:?WLXJURY_DB_USER must be set in .env}"
: "${WLXJURY_DB_PASSWORD:?WLXJURY_DB_PASSWORD must be set in .env}"

# ── Local container config ─────────────────────────────────────────────────────
CONTAINER_NAME="wlxjury-local-dev"
LOCAL_PORT=3307
LOCAL_DB="wlxjury"
LOCAL_USER="wlxjury_user"
LOCAL_PASSWORD="wlxjury_localpass"
LOCAL_ROOT_PASSWORD="wlxjury_rootpass"
DUMP_FILE="/tmp/wlxjury-prod.sql.gz"

echo "==> Config"
echo "    Prod host  : $WLXJURY_DB_HOST"
echo "    Prod DB    : $WLXJURY_DB"
echo "    Local port : $LOCAL_PORT"
echo "    Dump file  : $DUMP_FILE"
echo "    Skip dump  : $SKIP_DUMP"
echo "    Dump only  : $DUMP_ONLY"
```

- [ ] **Step 2: Make executable and verify it runs with --help**

```bash
chmod +x scripts/run-with-prod-dump.sh
./scripts/run-with-prod-dump.sh --help
```

Expected output:
```
#
# Dumps the production DB, restores it into a local MariaDB 10.6 Docker
# container, and starts the sbt dev server against it.
#
# Usage:
#   ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
#   ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
#   ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
```

- [ ] **Step 3: Verify bad flag exits non-zero**

```bash
./scripts/run-with-prod-dump.sh --not-a-flag; echo "exit: $?"
```

Expected: `Unknown argument: --not-a-flag` followed by `exit: 1`

- [ ] **Step 4: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add prod-dump local dev script scaffold"
```

---

### Task 2: Prod DB dump function

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

The dump is performed by spinning up a **temporary** `mariadb:10.6` Docker container with `docker run --rm`, which avoids requiring a local `mysqldump` binary.

- [ ] **Step 1: Append the dump function to the script**

Add after the constants block:

```bash
# ── Phase 1: Dump ──────────────────────────────────────────────────────────────
dump_prod_db() {
  echo "==> Dumping prod DB (via docker mariadb:10.6 client)..."
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
  echo "    Dump written to $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"
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
```

- [ ] **Step 2: Verify the script is valid shell syntax**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
```

Expected: `syntax OK`

- [ ] **Step 3: Run shellcheck (install if needed: brew install shellcheck)**

```bash
shellcheck scripts/run-with-prod-dump.sh
```

Expected: no output (no warnings)

- [ ] **Step 4: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add prod DB dump phase to script"
```

---

### Task 3: Start and health-check the local MariaDB container

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

- [ ] **Step 1: Append the container-start function**

Add after the dump block:

```bash
# ── Phase 2: Start local MariaDB container ────────────────────────────────────
start_local_container() {
  echo "==> Starting local MariaDB container '$CONTAINER_NAME'..."

  # Remove stale container if present
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

  echo -n "    Waiting for MariaDB to accept connections"
  local retries=60
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
}

start_local_container
```

- [ ] **Step 2: Test container lifecycle in isolation**

Temporarily comment out the dump block (or use `--skip-dump` with a fake file):

```bash
touch /tmp/wlxjury-prod.sql.gz
./scripts/run-with-prod-dump.sh --skip-dump --dump-only 2>&1 | head -20
```

(The script will fail at restore since the file is empty — that is expected at this stage. We just want to see "ready".)

Expected last line before error: `    Waiting for MariaDB to accept connections........ ready`

```bash
docker rm -f wlxjury-local-dev
rm /tmp/wlxjury-prod.sql.gz
```

- [ ] **Step 3: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add local MariaDB container start phase"
```

---

### Task 4: Restore dump into the local container

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

- [ ] **Step 1: Append the restore function**

Add after `start_local_container` call:

```bash
# ── Phase 3: Restore dump ─────────────────────────────────────────────────────
restore_dump() {
  echo "==> Restoring dump into local container..."
  zcat "$DUMP_FILE" \
    | docker exec -i "$CONTAINER_NAME" \
        mysql -u root "-p${LOCAL_ROOT_PASSWORD}" "$LOCAL_DB"
  echo "    Restore complete."
}

restore_dump
```

- [ ] **Step 2: Verify --dump-only exits after restore**

Add the early-exit gate right after `restore_dump`:

```bash
if [[ "$DUMP_ONLY" == true ]]; then
  echo "==> --dump-only set; skipping sbt run."
  echo "    Container '$CONTAINER_NAME' left running on port $LOCAL_PORT."
  echo "    Connect: mysql -h 127.0.0.1 -P $LOCAL_PORT -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
  exit 0
fi
```

- [ ] **Step 3: Smoke-test dump + restore against a real (non-prod) DB**

If you have any MariaDB accessible with the .env credentials, run:

```bash
./scripts/run-with-prod-dump.sh --dump-only
```

Expected output ends with:
```
==> Restoring dump into local container...
    Restore complete.
==> --dump-only set; skipping sbt run.
    Container 'wlxjury-local-dev' left running on port 3307.
    Connect: mysql -h 127.0.0.1 -P 3307 -u wlxjury_user -pwlxjury_localpass wlxjury
```

Verify data is present:
```bash
docker exec wlxjury-local-dev \
  mysql -u wlxjury_user -pwlxjury_localpass wlxjury \
  -e "SHOW TABLES;"
```

Expected: list of tables from production dump.

```bash
docker rm -f wlxjury-local-dev
```

- [ ] **Step 4: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add dump restore phase and --dump-only flag"
```

---

### Task 5: Launch sbt run against local container

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`

The sbt process must inherit env vars from `.env` (e.g. `COMMONS_USER`, `PLAY_SECRET`, etc.) but the four DB vars must be overridden to point to the local container. The JDBC URL uses `host:port` format (as seen in `conf/application.conf`).

- [ ] **Step 1: Append the sbt launch block**

Add after the `--dump-only` gate:

```bash
# ── Phase 4: Run dev server ────────────────────────────────────────────────────
echo "==> Starting sbt dev server against local prod dump..."
echo "    DB  : jdbc:mariadb://127.0.0.1:${LOCAL_PORT}/${LOCAL_DB}"
echo "    Stop: Ctrl-C (container '$CONTAINER_NAME' will remain; remove with: docker rm -f $CONTAINER_NAME)"
echo ""

WLXJURY_DB_HOST="127.0.0.1:${LOCAL_PORT}" \
WLXJURY_DB="$LOCAL_DB" \
WLXJURY_DB_USER="$LOCAL_USER" \
WLXJURY_DB_PASSWORD="$LOCAL_PASSWORD" \
  sbt run
```

Note: `conf/application.conf` builds the JDBC URL as `jdbc:mariadb://${WLXJURY_DB_HOST}/${WLXJURY_DB}`, so setting `WLXJURY_DB_HOST=127.0.0.1:3307` produces the correct `127.0.0.1:3307/wlxjury` URL.

- [ ] **Step 2: Verify syntax and shellcheck pass**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK` and no shellcheck warnings.

- [ ] **Step 3: Commit**

```bash
git add scripts/run-with-prod-dump.sh
git commit -m "feat: add sbt run phase to prod-dump local dev script"
```

---

### Task 6: Cleanup trap + README + end-to-end smoke test

**Files:**
- Modify: `scripts/run-with-prod-dump.sh`
- Create: `scripts/README.md`

The container should be left running when `--dump-only` is used (so the user can inspect it), but there's no automatic cleanup on Ctrl-C — that is intentional to allow re-runs with `--skip-dump`. Add a clear reminder message in the trap instead.

- [ ] **Step 1: Add a cleanup hint on exit (not auto-remove)**

Add right after the `set -euo pipefail` line, before any other code:

```bash
cleanup() {
  local exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    echo ""
    echo "Script exited with code $exit_code."
  fi
  echo "Container '$CONTAINER_NAME' may still be running."
  echo "  To remove it: docker rm -f $CONTAINER_NAME"
}
trap cleanup EXIT
```

Wait — `CONTAINER_NAME` is defined later. Move the `CONTAINER_NAME` constant block to immediately after the argument-parsing section so it is defined before the trap. The final ordering should be:

1. `set -euo pipefail`
2. `SCRIPT_DIR` / `REPO_DIR` / `ENV_FILE`
3. Argument parsing
4. `.env` loading + variable validation
5. **Constants block** (`CONTAINER_NAME`, `LOCAL_PORT`, etc.)
6. **`cleanup` trap**
7. Dump phase
8. Container start phase
9. Restore phase
10. `--dump-only` gate
11. sbt run phase

- [ ] **Step 2: Reorder the script to match the ordering above**

Final complete script content for `scripts/run-with-prod-dump.sh`:

```bash
#!/usr/bin/env bash
# scripts/run-with-prod-dump.sh
#
# Dumps the production DB, restores it into a local MariaDB 10.6 Docker
# container, and starts the sbt dev server against it.
#
# Usage:
#   ./scripts/run-with-prod-dump.sh            # full flow: dump → restore → run
#   ./scripts/run-with-prod-dump.sh --skip-dump # re-use existing /tmp/wlxjury-prod.sql.gz
#   ./scripts/run-with-prod-dump.sh --dump-only # dump and restore, do not start sbt
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="$REPO_DIR/.env"

# ── Argument parsing ───────────────────────────────────────────────────────────
SKIP_DUMP=false
DUMP_ONLY=false

for arg in "$@"; do
  case "$arg" in
    --skip-dump) SKIP_DUMP=true ;;
    --dump-only) DUMP_ONLY=true ;;
    --help|-h)
      sed -n '3,9p' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 1
      ;;
  esac
done

# ── Load prod credentials from .env ───────────────────────────────────────────
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: $ENV_FILE not found. Create it with WLXJURY_DB_HOST, WLXJURY_DB, WLXJURY_DB_USER, WLXJURY_DB_PASSWORD." >&2
  exit 1
fi
# shellcheck source=/dev/null
set -a; source "$ENV_FILE"; set +a

: "${WLXJURY_DB_HOST:?WLXJURY_DB_HOST must be set in .env}"
: "${WLXJURY_DB:?WLXJURY_DB must be set in .env}"
: "${WLXJURY_DB_USER:?WLXJURY_DB_USER must be set in .env}"
: "${WLXJURY_DB_PASSWORD:?WLXJURY_DB_PASSWORD must be set in .env}"

# ── Local container config ─────────────────────────────────────────────────────
CONTAINER_NAME="wlxjury-local-dev"
LOCAL_PORT=3307
LOCAL_DB="wlxjury"
LOCAL_USER="wlxjury_user"
LOCAL_PASSWORD="wlxjury_localpass"
LOCAL_ROOT_PASSWORD="wlxjury_rootpass"
DUMP_FILE="/tmp/wlxjury-prod.sql.gz"

# ── Cleanup hint on exit ───────────────────────────────────────────────────────
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
trap cleanup EXIT

echo "==> Config"
echo "    Prod host  : $WLXJURY_DB_HOST"
echo "    Prod DB    : $WLXJURY_DB"
echo "    Local port : $LOCAL_PORT"
echo "    Dump file  : $DUMP_FILE"
echo "    Skip dump  : $SKIP_DUMP"
echo "    Dump only  : $DUMP_ONLY"

# ── Phase 1: Dump ──────────────────────────────────────────────────────────────
dump_prod_db() {
  echo "==> Dumping prod DB (via docker mariadb:10.6 client)..."
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
  echo "    Dump written to $DUMP_FILE ($(du -sh "$DUMP_FILE" | cut -f1))"
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

# ── Phase 2: Start local MariaDB container ────────────────────────────────────
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

# ── Phase 3: Restore dump ─────────────────────────────────────────────────────
echo "==> Restoring dump into local container..."
zcat "$DUMP_FILE" \
  | docker exec -i "$CONTAINER_NAME" \
      mysql -u root "-p${LOCAL_ROOT_PASSWORD}" "$LOCAL_DB"
echo "    Restore complete."

if [[ "$DUMP_ONLY" == true ]]; then
  echo "==> --dump-only set; skipping sbt run."
  echo "    Container '$CONTAINER_NAME' left running on port $LOCAL_PORT."
  echo "    Connect: mysql -h 127.0.0.1 -P $LOCAL_PORT -u $LOCAL_USER -p$LOCAL_PASSWORD $LOCAL_DB"
  exit 0
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

- [ ] **Step 3: Final shellcheck and syntax check**

```bash
bash -n scripts/run-with-prod-dump.sh && echo "syntax OK"
shellcheck scripts/run-with-prod-dump.sh
```

Expected: `syntax OK`, no shellcheck warnings.

- [ ] **Step 4: Write scripts/README.md**

```markdown
# scripts/

## run-with-prod-dump.sh

Dumps the production DB, restores it into a local MariaDB 10.6 Docker container on port 3307, and starts the sbt dev server against it — so you can test the current branch against real production data without touching the live database.

**Requirements:** Docker (the script uses `mariadb:10.6` image — no local `mysql` client needed), a `.env` file at the repo root with `WLXJURY_DB_HOST`, `WLXJURY_DB`, `WLXJURY_DB_USER`, `WLXJURY_DB_PASSWORD`.

**Usage:**

```bash
# Full flow: dump prod → restore locally → run sbt
./scripts/run-with-prod-dump.sh

# Re-use the cached dump from /tmp/wlxjury-prod.sql.gz (faster for repeat runs)
./scripts/run-with-prod-dump.sh --skip-dump

# Dump and restore only — leave container running for manual inspection
./scripts/run-with-prod-dump.sh --dump-only
```

The container (`wlxjury-local-dev`) is left running after the script exits. To remove it:

```bash
docker rm -f wlxjury-local-dev
```
```

- [ ] **Step 5: End-to-end smoke test**

Run `--dump-only` to verify all phases work without launching sbt:

```bash
./scripts/run-with-prod-dump.sh --dump-only
```

Expected console output sequence:
1. `==> Config` block printed
2. `==> Dumping prod DB ...` → `Dump written to /tmp/wlxjury-prod.sql.gz`
3. `==> Starting local MariaDB container 'wlxjury-local-dev'...`
4. `    Waiting for MariaDB to accept connections......... ready`
5. `==> Restoring dump into local container... Restore complete.`
6. `==> --dump-only set; skipping sbt run.`

Verify tables restored:
```bash
docker exec wlxjury-local-dev \
  mysql -u wlxjury_user -pwlxjury_localpass wlxjury \
  -e "SELECT COUNT(*) FROM contest_jury;"
```

Expected: a row count matching production.

```bash
docker rm -f wlxjury-local-dev
```

- [ ] **Step 6: Full run (with prod credentials available)**

```bash
./scripts/run-with-prod-dump.sh
```

Expected: sbt dev server starts, Play application logs `Listening for HTTP on /0.0.0.0:9000`.
Open `http://localhost:9000` — should load the app with prod data.

- [ ] **Step 7: Commit**

```bash
git add scripts/run-with-prod-dump.sh scripts/README.md
git commit -m "feat: prod-dump local dev script — complete implementation"
```
