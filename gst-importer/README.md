# GST Master Importer

Downloads the GSTN e-invoice master code lists and imports them into the store's
PostgreSQL database. Ships as a Python CLI, a single-file `.exe` (PyInstaller),
a Flask API that triggers it, and a daily 2 AM IST cron job.

## What it imports

| Table (all prefixed `gst_importer_`) | Source |
|---|---|
| `gst_importer_state_master` | GSTN Master Codes page (HTML) |
| `gst_importer_country_master` | GSTN Master Codes page (HTML) |
| `gst_importer_currency_master` | GSTN Master Codes page (HTML) |
| `gst_importer_port_master` | GSTN Master Codes page (HTML) |
| `gst_importer_uqc_master` | GSTN Master Codes page (HTML) |
| `gst_importer_hsn_master` | Official HSN list (`hsn_master.json`) |
| `gst_importer_sac_master` | SAC codes from the same list (codes starting with `99`) |

Every table has the columns `id (PK), code (unique), description, gst_rate,
effective_date, updated_at`. `gst_rate`/`effective_date` are populated for
HSN/SAC rows from the CBIC rate seed; the code-list tables (state, country,
currency, port, UQC) have no rate by nature, so those two columns stay `NULL`.

The GSTN Master Codes page renders State/Country/Currency/Port/UQC tables
statically, but its **HSN and Tax Rates tabs are AJAX-driven** and are not in
the static HTML. HSN/SAC and rates therefore come from the official snapshots
that are bundled into the `.exe` (or overridden via `GST_HSN_FILE`/`GST_HSN_URL`
and `GST_RATES_FILE`/`GST_RATES_URL`).

**Why the prefix?** The store's Java backend owns `hsn_master`,
`state_master`, etc. as Hibernate-managed tables in the same database. This
importer writes only to its own `gst_importer_*` tables so the two can never
clash.

## Audit trail

Every run:
- appends a row to `gst_importer_import_logs` (`run_started_at`,
  `run_finished_at`, `status`, `trigger` = manual/cron/api, per-table
  `summary` JSON, or `error_detail` on failure), and
- appends a timestamped line to the log file (`logs/gst_master_update.log`,
  override with `GST_IMPORTER_LOG_FILE`).

## Quick start (dev)

```bash
cd gst-importer
python -m pip install -r requirements.txt
copy .env.example .env            # fill DB creds; dev defaults are pre-filled
python gst_master_importer.py     # run once
python gst_master_importer.py --trigger cron   # same thing, tagged for cron
```

## Build the .exe

```bat
build_exe.bat
```

Produces `dist\gst_master_importer.exe` (one file, HSN/SAC + rate seed bundled).
Test: `dist\gst_master_importer.exe --help`

## Flask API

```bash
python flask_api.py                 # http://127.0.0.1:5001
```

- `POST /update-gst-master` — runs the importer (the `.exe` if
  `GST_IMPORTER_EXE` is set, otherwise this script with the current Python)
  and returns `{status, started, finished, duration_seconds, exit_code,
  output_tail, summary}`.
- `GET /update-gst-master/status` — last run result.
- `GET /` — service health + last run.

Security: set `GST_IMPORTER_API_TOKEN`; requests must then send
`Authorization: Bearer <token>` (or `X-API-Key`). CORS origins come from
`CORS_ORIGINS`.

## Daily 2 AM IST cron

```bash
cd gst-importer/cron
./install_cron.sh        # run AS the user with database access
```

This installs `CRON_TZ=Asia/Kolkata` + `0 2 * * * gst_master_update.sh >> /var/log/gst_master_update.log 2>&1`.

`gst_master_update.sh`:
- appends `GST Master Codes updated successfully [timestamp]` on success, or
  the error output on failure, to `/var/log/gst_master_update.log`
  (`GST_MASTER_LOG` to change it);
- exits non-zero on failure so cron/monitoring can react;
- optionally posts to Slack (`SLACK_WEBHOOK_URL`) and/or emails
  (`ADMIN_EMAIL`, requires `mail`) on failure.

## Frontend integration

The admin "Update GST Master Codes" button (`frontend/src/admin/gst/
AdminGstRates.jsx`) first calls the importer API
(`VITE_GST_IMPORTER_URL/update-gst-master`, token in `VITE_GST_IMPORTER_TOKEN`)
and, if it is unreachable, falls back to the store's own Java refresh endpoint
(`POST /api/admin/gst-rates/refresh`). Point `VITE_GST_IMPORTER_URL` at the
Flask service to enable it; leave it unset to keep using only the Java path.
