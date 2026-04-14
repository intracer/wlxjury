#!/usr/bin/env bash
# Promotes the canary slot to active.
#
# 1. Replays MariaDB binlogs from the position saved by wlxjury-deploy-canary.sh
#    into the canary DB, bringing it current (takes seconds, not a minute).
# 2. Swaps /etc/apache2/wlxjury-slot.conf and /etc/wlxjury/slots.env.
# 3. Reloads Apache (~2 sec gap while workers drain).
#
# Run twice to rollback. After rollback, canary-sync-pos.txt is stale —
# re-run wlxjury-deploy-canary.sh before the next deployment.
#
# Run as root.

set -euo pipefail

SLOT_FILE=/etc/apache2/wlxjury-slot.conf
SYNC_POS=/etc/wlxjury/canary-sync-pos.txt
SLOTS_ENV=/etc/wlxjury/slots.env
BINLOG_DIR=/var/log/mysql

source "$SLOTS_ENV"   # ACTIVE_SLOT, CANARY_SLOT, ACTIVE_DB, CANARY_DB
source /etc/wlxjury/"$CANARY_SLOT".env  # WLXJURY_DB_USER, WLXJURY_DB_PASSWORD

DB_USER="$WLXJURY_DB_USER"
DB_PASS="$WLXJURY_DB_PASSWORD"

echo "=== Promoting $CANARY_SLOT → active (current active: $ACTIVE_SLOT) ==="

# 1. Replay binlogs from the position captured at canary-deploy time.
#    --to-last-log reads all binlog files from $LOG_FILE forward, handling rotation.
LOG_FILE=$(grep -oP "MASTER_LOG_FILE='\K[^']+" "$SYNC_POS")
LOG_POS=$(grep -oP  "MASTER_LOG_POS=\K[0-9]+"  "$SYNC_POS")
echo "Replaying binlogs from $LOG_FILE position $LOG_POS into $CANARY_DB..."
mysqlbinlog --start-position="$LOG_POS" --to-last-log "$BINLOG_DIR/$LOG_FILE" \
  | mysql -u"$DB_USER" -p"$DB_PASS" "$CANARY_DB"
echo "Binlog replay complete."

# 2. Swap Apache slot file and slots.env
if grep -q "WLXJURY_ACTIVE_PORT 9000" "$SLOT_FILE"; then
  # blue→active, green→canary  ⟹  swap to  green→active, blue→canary
  sed -i \
    's/WLXJURY_ACTIVE_PORT 9000/WLXJURY_ACTIVE_PORT 9001/;
     s/WLXJURY_CANARY_PORT 9001/WLXJURY_CANARY_PORT 9000/' "$SLOT_FILE"
  sed -i \
    's/ACTIVE_SLOT=blue/ACTIVE_SLOT=green/;
     s/CANARY_SLOT=green/CANARY_SLOT=blue/;
     s/ACTIVE_DB=wlxjury_blue/ACTIVE_DB=wlxjury_green/;
     s/CANARY_DB=wlxjury_green/CANARY_DB=wlxjury_blue/' "$SLOTS_ENV"
else
  # green→active, blue→canary  ⟹  swap to  blue→active, green→canary
  sed -i \
    's/WLXJURY_ACTIVE_PORT 9001/WLXJURY_ACTIVE_PORT 9000/;
     s/WLXJURY_CANARY_PORT 9000/WLXJURY_CANARY_PORT 9001/' "$SLOT_FILE"
  sed -i \
    's/ACTIVE_SLOT=green/ACTIVE_SLOT=blue/;
     s/CANARY_SLOT=blue/CANARY_SLOT=green/;
     s/ACTIVE_DB=wlxjury_green/ACTIVE_DB=wlxjury_blue/;
     s/CANARY_DB=wlxjury_blue/CANARY_DB=wlxjury_green/' "$SLOTS_ENV"
fi

# 3. Reload Apache (graceful — existing connections finish before workers restart)
systemctl reload apache2

echo ""
echo "=== Promotion complete ==="
grep "ACTIVE_SLOT\|CANARY_SLOT" "$SLOTS_ENV"
grep "ACTIVE_PORT\|CANARY_PORT" "$SLOT_FILE"
echo ""
echo "To rollback: run this script again."
echo "Writes made after this promotion will be lost on rollback."
