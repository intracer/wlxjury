# Blue-Green Promotion DB Sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace binlog replay in the blue-green promotion flow with a maintenance-window dump+restore that correctly handles schema migrations and ID conflicts.

**Architecture:** At promotion time, `promote.sh` enables a maintenance 503, dumps the active DB (including `flyway_schema_history`), restores into the canary DB, restarts the canary (Flyway runs only the new migration), health-checks, then swaps the Apache slot file. `deploy-canary.sh` loses all binlog-tracking code. The Apache config gains a maintenance flag RewriteRule on all 7 vhosts.

**Tech Stack:** Bash, MariaDB (`mysqldump --single-transaction`), Apache `mod_rewrite`, systemd.

**Spec:** `docs/superpowers/specs/2026-04-14-blue-green-promotion-sync-design.md`

---

## File Map

| Action | Path | Change |
|--------|------|--------|
| Modify | `bin/wlxjury-deploy-canary.sh` | Remove `SYNC_POS`, `--master-data=2`, binlog-position lines; renumber steps |
| Modify | `bin/wlxjury-promote.sh` | Full rewrite: maintenance mode, dump+restore, health check, rollback trap |
| Modify | `conf/apache/0002-wlxjury.conf` | Add maintenance `RewriteCond`/`RewriteRule`/`ErrorDocument` to all 7 vhosts |

---

## Task 1: Simplify `wlxjury-deploy-canary.sh`

**Files:**
- Modify: `bin/wlxjury-deploy-canary.sh`

Remove all binlog-tracking lines. The step that saved the binlog position (step 4) is deleted entirely; steps 5 and 6 become 4 and 5.

---

- [ ] **Step 1: Verify current syntax**

```bash
bash -n bin/wlxjury-deploy-canary.sh
```

Expected: no output (clean parse).

- [ ] **Step 2: Replace the file**

Write `bin/wlxjury-deploy-canary.sh` with this exact content:

```bash
#!/usr/bin/env bash
# Usage: wlxjury-deploy-canary.sh <path-to-wlxjury-X.Y.Z.zip>
#
# Deploys the given sbt-dist zip to the canary slot and seeds the canary DB
# from the active DB. Run as root (needs systemctl, mysql access).
#
# Prerequisites:
#   /etc/wlxjury/slots.env   — slot state file (see conf/wlxjury/slots.env.example)
#   /etc/wlxjury/<slot>.env  — per-slot env files (see conf/wlxjury/*.env.example)

set -euo pipefail

ZIP="${1:?Usage: $0 <path-to-wlxjury.zip>}"
SLOTS_ENV=/etc/wlxjury/slots.env
EXTRACT_TMP="/tmp/wlxjury-extract-$$"

source "$SLOTS_ENV"   # ACTIVE_SLOT, CANARY_SLOT, ACTIVE_DB, CANARY_DB
source /etc/wlxjury/"$CANARY_SLOT".env  # WLXJURY_DB_USER, WLXJURY_DB_PASSWORD

DB_USER="$WLXJURY_DB_USER"
DB_PASS="$WLXJURY_DB_PASSWORD"
INSTALL_DIR="/opt/wlxjury-$CANARY_SLOT"
DUMP_FILE="/tmp/wlxjury-sync-$$.sql"

echo "=== Deploying $ZIP to $CANARY_SLOT slot ==="

# 1. Stop canary service if running
echo "Stopping wlxjury-$CANARY_SLOT (if running)..."
systemctl stop "wlxjury-$CANARY_SLOT" 2>/dev/null || true

# 2. Extract zip to slot install directory
# sbt dist produces a zip with a single top-level versioned directory inside.
echo "Extracting $ZIP to $INSTALL_DIR..."
rm -rf "$INSTALL_DIR" "$EXTRACT_TMP"
mkdir -p "$EXTRACT_TMP"
unzip -q "$ZIP" -d "$EXTRACT_TMP"
# Move the single versioned subdirectory into place
mkdir -p "$INSTALL_DIR"
shopt -s dotglob
mv "$EXTRACT_TMP"/*/  "$INSTALL_DIR/" 2>/dev/null || mv "$EXTRACT_TMP"/* "$INSTALL_DIR/"
shopt -u dotglob
rm -rf "$EXTRACT_TMP"
chmod +x "$INSTALL_DIR/bin/wlxjury"
echo "Extracted to $INSTALL_DIR"

# 3. Dump active DB
# flyway_schema_history is included so promote.sh only runs the new migration.
echo "Dumping active DB ($ACTIVE_DB)..."
mysqldump --single-transaction \
  -u"$DB_USER" -p"$DB_PASS" "$ACTIVE_DB" > "$DUMP_FILE"

# 4. Restore dump into canary DB (wipe first so Flyway starts from clean slate)
echo "Restoring into canary DB ($CANARY_DB)..."
mysql -u"$DB_USER" -p"$DB_PASS" \
  -e "DROP DATABASE IF EXISTS \`$CANARY_DB\`; \
      CREATE DATABASE \`$CANARY_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u"$DB_USER" -p"$DB_PASS" "$CANARY_DB" < "$DUMP_FILE"
rm -f "$DUMP_FILE"
echo "Restored $CANARY_DB"

# 5. Start canary — Flyway migrates the canary DB schema on startup
echo "Starting wlxjury-$CANARY_SLOT..."
systemctl start "wlxjury-$CANARY_SLOT"

echo ""
echo "=== Canary deployed ==="
echo "  Slot:    $CANARY_SLOT  (/opt/wlxjury-$CANARY_SLOT)"
echo "  DB:      $CANARY_DB"
echo "  Test at: https://jury-canary.wle.org.ua"
echo ""
echo "When satisfied, run: wlxjury-promote.sh"
```

- [ ] **Step 3: Verify syntax of the new file**

```bash
bash -n bin/wlxjury-deploy-canary.sh
```

Expected: no output.

- [ ] **Step 4: Verify the file is still executable**

```bash
ls -l bin/wlxjury-deploy-canary.sh
```

Expected: `-rwxr-xr-x` permissions. If not: `chmod +x bin/wlxjury-deploy-canary.sh`

- [ ] **Step 5: Commit**

```bash
git add bin/wlxjury-deploy-canary.sh
git commit -m "fix: remove binlog tracking from deploy-canary.sh"
```

---

## Task 2: Rewrite `wlxjury-promote.sh`

**Files:**
- Modify: `bin/wlxjury-promote.sh`

Full rewrite. The binlog replay block is replaced with: maintenance mode → dump → restore → canary restart → health check → slot swap → remove maintenance flag. An `ERR` trap handles rollback if any step fails before the slot swap.

**Note on HTTPS vhosts:** The maintenance `RewriteRule` (Task 3) is added to the HTTP vhosts in our repo. Certbot-generated HTTPS vhosts on the server also need the same rule for a complete write-blocking window. The operator must add it manually (or re-run Certbot after Task 3 is deployed). Without it, HTTPS writes that arrive between dump start and slot switch are lost — acceptable for the stated maintenance-window trade-off.

---

- [ ] **Step 1: Replace the file**

Write `bin/wlxjury-promote.sh` with this exact content:

```bash
#!/usr/bin/env bash
# Promotes the canary slot to active via a maintenance-window dump+restore.
#
# 1. Enables maintenance mode — all vhosts return 503 (see conf/apache/0002-wlxjury.conf).
# 2. Dumps the active DB into the canary DB (flyway_schema_history included,
#    so Flyway only runs the new migration on restart — not all historical ones).
# 3. Restarts the canary service; Flyway applies the delta migration only.
# 4. Waits for the canary to pass a health check (HTTP 200 on canary port).
# 5. Swaps the Apache slot file and reloads Apache (traffic switches to canary).
# 6. Removes the maintenance flag — all vhosts serve normally again.
#
# On failure before the slot swap: rollback removes the maintenance flag and
# reloads Apache — the active service was never stopped, so traffic returns.
#
# To rollback after a successful promotion: run this script again. The dump+restore
# runs in the other direction. Note: the backup slot (old active) will have the new
# schema after rollback; old code may not work if the migration was a breaking change.
#
# Run as root.

set -euo pipefail

SLOT_FILE=/etc/apache2/wlxjury-slot.conf
SLOTS_ENV=/etc/wlxjury/slots.env
MAINTENANCE=/etc/wlxjury/maintenance
HEALTH_TIMEOUT=120   # seconds

source "$SLOTS_ENV"   # ACTIVE_SLOT, CANARY_SLOT, ACTIVE_DB, CANARY_DB
source /etc/wlxjury/"$CANARY_SLOT".env  # WLXJURY_DB_USER, WLXJURY_DB_PASSWORD

DB_USER="$WLXJURY_DB_USER"
DB_PASS="$WLXJURY_DB_PASSWORD"
DUMP_FILE="/tmp/wlxjury-promote-$$.sql"

# Read CANARY_PORT from the slot file for the health check
CANARY_PORT=$(grep -oP 'WLXJURY_CANARY_PORT \K[0-9]+' "$SLOT_FILE")

echo "=== Promoting $CANARY_SLOT → active (current active: $ACTIVE_SLOT) ==="
echo "    Maintenance window begins — all vhosts will return 503"

# ── Rollback on any error before the slot swap ────────────────────────────────
rollback() {
  echo "ROLLBACK: removing maintenance flag and reloading Apache." >&2
  rm -f "$MAINTENANCE"
  systemctl reload apache2
  echo "Active slot ($ACTIVE_SLOT) is still serving traffic." >&2
  exit 1
}
trap rollback ERR

# 1. Enable maintenance mode
touch "$MAINTENANCE"
systemctl reload apache2

# 2. Dump active DB (flyway_schema_history included)
echo "Dumping active DB ($ACTIVE_DB)..."
mysqldump --single-transaction \
  -u"$DB_USER" -p"$DB_PASS" "$ACTIVE_DB" > "$DUMP_FILE"

# 3. Drop + recreate canary DB, restore dump
echo "Restoring into canary DB ($CANARY_DB)..."
mysql -u"$DB_USER" -p"$DB_PASS" \
  -e "DROP DATABASE IF EXISTS \`$CANARY_DB\`; \
      CREATE DATABASE \`$CANARY_DB\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u"$DB_USER" -p"$DB_PASS" "$CANARY_DB" < "$DUMP_FILE"
rm -f "$DUMP_FILE"

# 4. Restart canary — Flyway runs the new migration only
echo "Restarting wlxjury-$CANARY_SLOT (Flyway will apply delta migration)..."
systemctl restart "wlxjury-$CANARY_SLOT"

# 5. Health check loop
echo "Waiting for canary health check on port $CANARY_PORT (timeout: ${HEALTH_TIMEOUT}s)..."
ELAPSED=0
until curl -sf "http://127.0.0.1:$CANARY_PORT/" -o /dev/null; do
  sleep 5
  ELAPSED=$((ELAPSED + 5))
  if [[ $ELAPSED -ge $HEALTH_TIMEOUT ]]; then
    echo "Health check timed out after ${HEALTH_TIMEOUT}s." >&2
    exit 1   # triggers rollback trap
  fi
  echo "  ...still waiting (${ELAPSED}s elapsed)"
done
echo "Canary is healthy."

# Disable ERR trap — slot swap and Apache reload errors should not trigger rollback
trap - ERR

# 6. Swap slot file and slots.env
if grep -q "WLXJURY_ACTIVE_PORT 9000" "$SLOT_FILE"; then
  # blue→active  ⟹  swap to  green→active
  sed -i \
    's/WLXJURY_ACTIVE_PORT 9000/WLXJURY_ACTIVE_PORT 9001/;
     s/WLXJURY_CANARY_PORT 9001/WLXJURY_CANARY_PORT 9000/' "$SLOT_FILE"
  sed -i \
    's/ACTIVE_SLOT=blue/ACTIVE_SLOT=green/;
     s/CANARY_SLOT=green/CANARY_SLOT=blue/;
     s/ACTIVE_DB=wlxjury_blue/ACTIVE_DB=wlxjury_green/;
     s/CANARY_DB=wlxjury_green/CANARY_DB=wlxjury_blue/' "$SLOTS_ENV"
else
  # green→active  ⟹  swap to  blue→active
  sed -i \
    's/WLXJURY_ACTIVE_PORT 9001/WLXJURY_ACTIVE_PORT 9000/;
     s/WLXJURY_CANARY_PORT 9000/WLXJURY_CANARY_PORT 9001/' "$SLOT_FILE"
  sed -i \
    's/ACTIVE_SLOT=green/ACTIVE_SLOT=blue/;
     s/CANARY_SLOT=blue/CANARY_SLOT=green/;
     s/ACTIVE_DB=wlxjury_green/ACTIVE_DB=wlxjury_blue/;
     s/CANARY_DB=wlxjury_blue/CANARY_DB=wlxjury_green/' "$SLOTS_ENV"
fi
systemctl reload apache2

# 7. Remove maintenance flag
rm -f "$MAINTENANCE"

echo ""
echo "=== Promotion complete ==="
grep "ACTIVE_SLOT\|CANARY_SLOT" "$SLOTS_ENV"
grep "ACTIVE_PORT\|CANARY_PORT" "$SLOT_FILE"
echo ""
echo "To rollback: run this script again (full dump+restore in reverse)."
echo "Note: on rollback the old-active slot will have the new schema applied."
echo "Old code may not work if the migration was a breaking change."
```

- [ ] **Step 2: Verify syntax**

```bash
bash -n bin/wlxjury-promote.sh
```

Expected: no output.

- [ ] **Step 3: Verify executable**

```bash
ls -l bin/wlxjury-promote.sh
```

Expected: `-rwxr-xr-x`. If not: `chmod +x bin/wlxjury-promote.sh`

- [ ] **Step 4: Commit**

```bash
git add bin/wlxjury-promote.sh
git commit -m "fix: replace binlog replay with dump+restore+health-check in promote.sh"
```

---

## Task 3: Add maintenance RewriteRule to all 7 vhosts

**Files:**
- Modify: `conf/apache/0002-wlxjury.conf`

Add the same three-line maintenance block to every `<VirtualHost *:80>` block (6 production + 1 canary). VirtualHost 2 (`jury.wikilovesearth.org`) currently has no `RewriteEngine` directive — add it together with the maintenance lines.

The maintenance block must come **before** any other `RewriteCond`/`RewriteRule` lines so it intercepts before the HTTPS redirect fires.

---

- [ ] **Step 1: Write the new `conf/apache/0002-wlxjury.conf`**

```apache
# Include the slot file so ${WLXJURY_ACTIVE_PORT} and ${WLXJURY_CANARY_PORT} are defined.
# Install wlxjury-slot.conf to /etc/apache2/ on the server.
Include /etc/apache2/wlxjury-slot.conf

# ── Production virtual hosts — route to active slot ──────────────────────────

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName jury.wikilovesearth.org.ua
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
    RewriteCond %{SERVER_NAME} =jury.wikilovesearth.org.ua
    RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName jury.wikilovesearth.org
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
</VirtualHost>

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName jury.wikilovesmonuments.org.ua
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
    RewriteCond %{SERVER_NAME} =jury.wikilovesmonuments.org.ua
    RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName jury.wle.org.ua
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
    RewriteCond %{SERVER_NAME} =jury.wle.org.ua
    RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName jury.wlm.org.ua
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
    RewriteCond %{SERVER_NAME} =jury.wlm.org.ua
    RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName wlxjury.wikimedia.in.ua
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_ACTIVE_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
    RewriteCond %{SERVER_NAME} =jury.wle.org.ua [OR]
    RewriteCond %{SERVER_NAME} =wlxjury.wikimedia.in.ua
    RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>

# ── Canary virtual host — testers only ───────────────────────────────────────
# Add DNS entry for jury-canary.wle.org.ua pointing to this server.
# Add TLS certificate (e.g. via Certbot) for jury-canary.wle.org.ua.
#
# NOTE: the Certbot-generated HTTPS vhosts on the server also need the same
# maintenance RewriteCond/RewriteRule/ErrorDocument added manually (or via
# re-running Certbot after deploying this file) for the maintenance window to
# cover HTTPS traffic completely.

<VirtualHost *:80>
    ProxyPreserveHost On
    ServerName jury-canary.wle.org.ua
    ProxyPass    /excluded ! retry=0
    ProxyPass    / http://127.0.0.1:${WLXJURY_CANARY_PORT}/ retry=0
    ProxyPassReverse / http://127.0.0.1:${WLXJURY_CANARY_PORT}/
    RewriteEngine on
    RewriteCond /etc/wlxjury/maintenance -f
    RewriteRule ^ - [R=503,L]
    ErrorDocument 503 "Jury tool is undergoing a brief maintenance (typically 2-5 minutes). Please try again shortly."
    RewriteCond %{SERVER_NAME} =jury-canary.wle.org.ua
    RewriteRule ^ https://%{SERVER_NAME}%{REQUEST_URI} [END,NE,R=permanent]
</VirtualHost>
```

- [ ] **Step 2: Commit**

```bash
git add conf/apache/0002-wlxjury.conf
git commit -m "feat: add maintenance-mode 503 to all Apache vhosts"
```
