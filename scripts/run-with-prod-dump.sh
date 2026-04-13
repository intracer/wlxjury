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
