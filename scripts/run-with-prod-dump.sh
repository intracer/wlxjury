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
