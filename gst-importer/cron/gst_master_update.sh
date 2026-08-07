#!/usr/bin/env bash
# Daily GST master code update.
#
# Scheduled by install_cron.sh at "0 2 * * *" with CRON_TZ=Asia/Kolkata, i.e.
# 2 AM IST every day. This wrapper appends everything to the log file and exits
# with the importer's exit code so cron can report problems.
#
# Requirements before installing:
#   * GST_IMPORTER_EXE (or the IMPORTER default below) points at the packaged
#     gst_master_importer.exe
#   * The user this runs as (crontab -e as that user) can reach the database
#     and read the importer's .env
#
# Optional alerts: set SLACK_WEBHOOK_URL and/or ADMIN_EMAIL in the environment
# or in .env to be told when the daily run fails.

set -u

IMPORTER="${GST_IMPORTER_EXE:-/path/to/gst_master_importer.exe}"
LOG="${GST_MASTER_LOG:-/var/log/gst_master_update.log}"
SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
ADMIN_EMAIL="${ADMIN_EMAIL:-}"

ENV_FILE="$(cd "$(dirname "$0")" && pwd)/../.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  . "$ENV_FILE"
  set +a
fi

now() { date '+%Y-%m-%d %H:%M:%S %Z'; }
log_line() { echo "[$(now)] $*" >> "$LOG"; }

mkdir -p "$(dirname "$LOG")"
log_line "GST master update started"

if [ ! -x "$IMPORTER" ]; then
  log_line "FAILED: importer not found or not executable: $IMPORTER"
  log_line "GST Master Codes update FAILED with exit code 1"
  exit 1
fi

if "$IMPORTER" --trigger cron >> "$LOG" 2>&1; then
  log_line "GST Master Codes updated successfully [$(now)]"
  exit 0
else
  rc=$?
  log_line "GST Master Codes update FAILED with exit code $rc"
  log_line "Details above in this log - search for 'ERROR' or 'Traceback'."

  if [ -n "$SLACK_WEBHOOK_URL" ]; then
    if curl -s -X POST -H 'Content-type: application/json' \
        --data "{\"text\":\"GST Master Codes update FAILED on $(hostname) (exit $rc) at $(now). See $LOG\"}" \
        "$SLACK_WEBHOOK_URL" >> "$LOG" 2>&1; then
      log_line "Slack alert sent"
    else
      log_line "Slack alert failed to send"
    fi
  fi

  if [ -n "$ADMIN_EMAIL" ] && command -v mail >/dev/null 2>&1; then
    if { echo "GST Master Codes update FAILED (exit $rc) at $(now)."; echo "Details: $LOG"; } \
        | mail -s "GST Master Codes update FAILED" "$ADMIN_EMAIL" >> "$LOG" 2>&1; then
      log_line "Email alert sent"
    else
      log_line "Email alert failed to send"
    fi
  fi

  exit "$rc"
fi
