#!/usr/bin/env bash
# Installs the daily 2 AM IST cron entry for the GST master importer.
#
# Run this as the user that should run the importer (the one with database
# access). It adds:
#
#   CRON_TZ=Asia/Kolkata
#   0 2 * * * <this dir>/gst_master_update.sh >> /var/log/gst_master_update.log 2>&1
#
# CRON_TZ pins the schedule to IST so "2 AM" means 2 AM India time even on a
# UTC server. On cron versions without CRON_TZ support, edit /etc/crontab or
# set the server timezone to Asia/Kolkata first.

set -u

DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER="$DIR/gst_master_update.sh"
LOG="${GST_MASTER_LOG:-/var/log/gst_master_update.log}"

if [ ! -f "$WRAPPER" ]; then
  echo "ERROR: wrapper not found at $WRAPPER"
  exit 1
fi

echo "Install as user: $(id -un)"
echo "Make sure GST_IMPORTER_EXE in $DIR/../.env (or the wrapper default)"
echo "points at your gst_master_importer.exe, and that this user can read .env"
echo "and reach the database."
read -r -p "Install cron entry '0 2 * * *'? [y/N] " ans
case "$ans" in
  y|Y) ;;
  *) echo "Aborted."; exit 0 ;;
esac

chmod +x "$WRAPPER"
mkdir -p "$(dirname "$LOG")"

# Keep one importer entry, preserve any other crontab content.
CRON_TMP="$(mktemp)"
( crontab -l 2>/dev/null | grep -vF "gst_master_update.sh"; \
  echo "CRON_TZ=Asia/Kolkata"; \
  echo "0 2 * * * $WRAPPER >> $LOG 2>&1" ) | crontab -
rm -f "$CRON_TMP"

echo "Installed. Verify with:  crontab -l"
echo "The job runs under user $(id -un), so that user needs DB access."
echo "Watch it with:  tail -f $LOG"
