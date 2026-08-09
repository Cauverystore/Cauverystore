# -*- coding: utf-8 -*-
"""
Configuration for the GST master importer and its Flask API.

Reads environment variables, falling back to a .env file next to this module.
The importer connects to the same PostgreSQL database the store uses (Railway
Postgres on production, local Postgres in dev). All tables it owns are prefixed
`gst_importer_` so it can never collide with the store's Hibernate tables.
"""

import os
import sys


def _load_dotenv(path=None):
    path = path or os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")
    if os.path.exists(path):
        with open(path, "r", encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line or line.startswith("#") or "=" not in line:
                    continue
                key, _, value = line.partition("=")
                os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


_load_dotenv()


def get(key, default=None):
    return os.environ.get(key, default)


def db_dsn():
    """A libpq DSN string. Prefers DATABASE_URL, falls back to PG* vars."""
    url = get("DATABASE_URL")
    if url:
        return url
    return "postgresql://{user}:{password}@{host}:{port}/{db}".format(
        user=get("PGUSER", "postgres"),
        password=get("PGPASSWORD", ""),
        host=get("PGHOST", "localhost"),
        port=get("PGPORT", "5432"),
        db=get("PGDATABASE", "postgres"),
    )


def table_prefix():
    return get("GST_IMPORTER_TABLE_PREFIX", "gst_importer_")


def master_codes_url():
    return get("GST_MASTER_CODES_URL", "https://einvoice1.gst.gov.in/Others/MasterCodes")


def master_codes_html():
    """A locally saved copy of the Master Codes page, or None to fetch live."""
    path = get("GST_MASTER_CODES_FILE")
    if path and os.path.exists(path):
        return path
    bundled = os.path.join(_bundle_dir(), "data", "master_codes.html")
    if os.path.exists(bundled):
        return bundled
    return None


def hsn_source():
    """('file'|'url'|None, path-or-url). Bundled JSON ships with the exe."""
    path = get("GST_HSN_FILE")
    if path and os.path.exists(path):
        return ("file", path)
    url = get("GST_HSN_URL")
    if url:
        return ("url", url)
    bundled = os.path.join(_bundle_dir(), "data", "hsn_master.json")
    if os.path.exists(bundled):
        return ("file", bundled)
    return (None, None)


def rates_source():
    """('file'|'url'|None, path-or-url). Bundled CBIC seed ships with the exe."""
    path = get("GST_RATES_FILE")
    if path and os.path.exists(path):
        return ("file", path)
    url = get("GST_RATES_URL")
    if url:
        return ("url", url)
    bundled = os.path.join(_bundle_dir(), "data", "gst_rate_seed.json")
    if os.path.exists(bundled):
        return ("file", bundled)
    return (None, None)


def log_file():
    return get("GST_IMPORTER_LOG_FILE", os.path.join(os.path.dirname(os.path.abspath(__file__)), "logs", "gst_master_update.log"))


def api_token():
    return get("GST_IMPORTER_API_TOKEN", "")


def cors_origins():
    return [o.strip() for o in get("CORS_ORIGINS", "http://localhost:3000").split(",") if o.strip()]


def importer_exe():
    return get("GST_IMPORTER_EXE", "")


def run_timeout():
    return int(get("GST_IMPORTER_TIMEOUT", "600"))


def _bundle_dir():
    """The folder PyInstaller unpacks bundled files into when frozen."""
    return getattr(sys, "_MEIPASS", os.path.dirname(os.path.abspath(__file__)))
