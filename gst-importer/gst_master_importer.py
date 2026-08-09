# -*- coding: utf-8 -*-
"""
GST Master Importer - downloads the GSTN e-invoice master code lists and imports
them into PostgreSQL.

Sources:
  * Master Codes page  (https://einvoice1.gst.gov.in/Others/MasterCodes)
      -> State, Country, Currency, Port, UQC tables (server-rendered HTML).
      The page's HSN and Tax-Rates tabs are AJAX-driven and not in the static
      HTML, so those two lists come from the official snapshots bundled with
      the build (or overridden by GST_HSN_FILE / GST_HSN_URL and
      GST_RATES_FILE / GST_RATES_URL).
  * HSN/SAC list       -> hsn_master.json (official HSN + SAC master; SAC codes
      are the ones starting with "99").
  * CBIC rate seed     -> gst_rate_seed.json (parsed from the CBIC notification
      PDFs) supplies gst_rate and effective_date for HSN/SAC rows.

All owned tables are prefixed `gst_importer_` (see config.table_prefix) so this
can never collide with the store's own Hibernate-managed master tables.

Audit: every run appends a row to gst_importer_import_logs and a line to the
log file with the per-table inserted/updated counts and the trigger that
started it (manual / cron / api).

Exit code is 0 on success, 1 on any failure - the cron wrapper keys off that.
"""

import argparse
import json
import os
import re
import sys
import time
import urllib.request

import pandas as pd
import psycopg2

import config as cfg

TAG_RE = re.compile(r"<[^>]+>")

# Table headers as they appear on the Master Codes page, keyed by target table
# name (without prefix). Header text drifts - we match on keywords, not exact.
PAGE_TABLES = [
    ("state_master",    ["state code", "state name"],          ("code", "description")),
    ("country_master",  ["country code", "country name"],      ("code", "description")),
    ("currency_master", ["currency code", "currency name"],    ("code", "description")),
    ("port_master",     ["port code", "port name"],            ("code", "description")),
    ("uqc_master",      ["unit code", "unit description"],     ("code", "description")),
]


def log(msg):
    print(msg, flush=True)


def clean(cell):
    text = TAG_RE.sub("", cell)
    for entity, char in (("&nbsp;", " "), ("&amp;", "&"), ("&lt;", "<"),
                         ("&gt;", ">"), ("&#39;", "'"), ("&quot;", '"')):
        text = text.replace(entity, char)
    return re.sub(r"\s+", " ", text).strip()


def parse_tables(html):
    """Every table on the page as a list of rows, each row a list of cells."""
    out = []
    for table in re.findall(r"<table[^>]*>(.*?)</table>", html, re.S | re.I):
        rows = []
        for row in re.findall(r"<tr[^>]*>(.*?)</tr>", table, re.S | re.I):
            cells = [clean(c) for c in re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", row, re.S | re.I)]
            if any(cells):
                rows.append(cells)
        if rows:
            out.append(rows)
    return out


def header_matches(header, expected):
    """Every key word of each expected header must appear (so "state code state
    name" cannot match the country pattern just because both use code+name)."""
    joined = " ".join(header).lower()
    return all(all(word in joined for word in exp.split()) for exp in expected)


def fetch_text(url, timeout=90, attempts=3):
    """GET a URL, retrying transient failures (GSTN drops connections)."""
    last_error = None
    for attempt in range(1, attempts + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read().decode("utf-8", errors="replace")
        except Exception as exc:  # noqa: BLE001 - retried below
            last_error = exc
            if attempt < attempts:
                time.sleep(attempt * 5)
    raise last_error


def fetch_json(url, timeout=90):
    data = fetch_text(url, timeout)
    return json.loads(data)


def fetch_master_codes():
    """Returns {table: [(code, description), ...]} for the page's static tables."""
    saved = cfg.master_codes_html()
    if saved:
        log("page: reading saved HTML %s" % saved)
        with open(saved, "r", encoding="utf-8", errors="replace") as fh:
            html = fh.read()
    elif not cfg.page_fetch_enabled():
        log("WARN  live page fetch disabled (GST_PAGE_FETCH=0) - page tables left unchanged")
        return {}
    else:
        html = fetch_text(cfg.master_codes_url())
    tables = parse_tables(html)
    found = {}
    for filename, expected, keys in PAGE_TABLES:
        matched = None
        for rows in tables:
            header = rows[0] if rows else []
            if header_matches(header, expected):
                matched = rows
                break
        if matched is None:
            log("WARN  table '%s' not found on the Master Codes page - skipping" % filename)
            continue
        records = []
        for cells in matched[1:]:
            if len(cells) < len(keys):
                continue
            code, description = cells[0], cells[1]
            # GSTINs carry the state code padded to two digits; the portal
            # prints it unpadded ("1" vs "01" is how POS checks silently fail).
            if filename == "state_master" and code.isdigit():
                code = code.zfill(2)
            if code:
                records.append((code, description))
        found[filename] = records
        log("OK    %-16s %5d rows" % (filename, len(records)))
    return found


def load_hsn_sac():
    """Splits the official HSN/SAC list. SAC codes all start with '99'."""
    kind, source = cfg.hsn_source()
    if kind is None:
        log("WARN  no HSN/SAC source (set GST_HSN_FILE or GST_HSN_URL) - skipping")
        return [], []
    if kind == "file":
        with open(source, "r", encoding="utf-8") as fh:
            data = json.load(fh)
    else:
        data = fetch_json(source)
    df = pd.json_normalize(data)
    if "code" not in df.columns:
        raise RuntimeError("HSN/SAC source has no 'code' column: %s" % source)
    df["code"] = df["code"].astype(str).str.strip()
    df["description"] = df.get("description", df.get("name", "")).fillna("").astype(str)
    hsn = df[~df["code"].str.startswith("99")]
    sac = df[df["code"].str.startswith("99")]
    log("OK    hsn_master    %5d rows" % len(hsn))
    log("OK    sac_master    %5d rows" % len(sac))
    return ([(c, d) for c, d in zip(hsn["code"], hsn["description"])],
            [(c, d) for c, d in zip(sac["code"], sac["description"])])


def load_rates():
    """Returns {code: (gst_rate, effective_date)} from the CBIC seed."""
    kind, source = cfg.rates_source()
    if kind is None:
        log("WARN  no rates source (set GST_RATES_FILE or GST_RATES_URL) - gst_rate left null")
        return {}
    if kind == "file":
        with open(source, "r", encoding="utf-8") as fh:
            data = json.load(fh)
    else:
        data = fetch_json(source)
    df = pd.json_normalize(data)
    if "hsnCode" not in df.columns or "gstRate" not in df.columns:
        raise RuntimeError("Rates source has no hsnCode/gstRate columns: %s" % source)
    df["hsnCode"] = df["hsnCode"].astype(str).str.strip()
    rates = {}
    for _, row in df.iterrows():
        rates[row["hsnCode"]] = (row["gstRate"], row.get("effectiveFrom"))
    log("OK    rates         %5d rows loaded" % len(rates))
    return rates


def load_master_sheet(path):
    """Reads the Excel master workbook (sheets: hsn, sac, rates, and the five
    code-list sheets) and returns (page, hsn, sac, rates) like the live flow."""
    excel = pd.ExcelFile(path)

    def sheet_rows(name):
        if name not in excel.sheet_names:
            log("WARN  sheet '%s' missing from master workbook - skipping" % name)
            return None
        df = excel.parse(name, dtype=str).fillna("")
        return df

    page = {}
    for filename, _, _ in PAGE_TABLES:
        df = sheet_rows(filename)
        if df is None:
            continue
        records = []
        for code, description in df.values.tolist():
            code = str(code).strip()
            if filename == "state_master" and code.isdigit():
                code = code.zfill(2)
            if code:
                records.append((code, str(description).strip()))
        page[filename] = records
        log("OK    %-16s %5d rows" % (filename, len(records)))

    hsn = sac = []
    df = sheet_rows("hsn")
    if df is not None:
        hsn = [(str(c).strip(), str(d).strip()) for c, d in df[["code", "description"]].values.tolist()]
        log("OK    %-16s %5d rows" % ("hsn_master", len(hsn)))
    df = sheet_rows("sac")
    if df is not None:
        sac = [(str(c).strip(), str(d).strip()) for c, d in df[["code", "description"]].values.tolist()]
        log("OK    %-16s %5d rows" % ("sac_master", len(sac)))

    rates = {}
    df = sheet_rows("rates")
    if df is not None:
        for _, row in df.iterrows():
            code = str(row.get("code", "")).strip()
            if not code or code == "nan":
                continue
            rate = row.get("gst_rate", "")
            eff = row.get("effective_date", "")
            if rate in ("", "nan"):
                rate = None
            if eff in ("", "nan", "None"):
                eff = None
            rates[code] = (rate, eff)
        log("OK    %-16s %5d rows loaded" % ("rates", len(rates)))
    return page, hsn, sac, rates


def ensure_schema(conn, tables):
    """Creates the prefixed master tables and the audit log table if missing."""
    with conn.cursor() as cur:
        for table in tables:
            cur.execute("""
                CREATE TABLE IF NOT EXISTS {0} (
                    id             BIGSERIAL PRIMARY KEY,
                    code           TEXT NOT NULL UNIQUE,
                    description    TEXT,
                    gst_rate       NUMERIC(8, 2),
                    effective_date DATE,
                    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
                )
            """.format(table))
        cur.execute("""
            CREATE TABLE IF NOT EXISTS gst_importer_import_logs (
                id              BIGSERIAL PRIMARY KEY,
                run_started_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
                run_finished_at TIMESTAMPTZ,
                status          TEXT NOT NULL,
                trigger         TEXT NOT NULL,
                summary         JSONB,
                error_detail    TEXT
            )
        """)
    conn.commit()


UPSERT = """
    INSERT INTO {table} (code, description, gst_rate, effective_date)
    VALUES (%s, %s, %s, %s)
    ON CONFLICT (code) DO UPDATE SET
        description    = EXCLUDED.description,
        gst_rate       = EXCLUDED.gst_rate,
        effective_date = EXCLUDED.effective_date,
        updated_at     = now()
    RETURNING (xmax = 0) AS inserted
"""


def upsert(conn, table, rows):
    """Upserts rows of (code, description, gst_rate, effective_date)."""
    inserted = 0
    updated = 0
    with conn.cursor() as cur:
        for row in rows:
            cur.execute(UPSERT.format(table=table), row)
            if cur.fetchone()[0]:
                inserted += 1
            else:
                updated += 1
    conn.commit()
    return inserted, updated


def main(argv=None):
    parser = argparse.ArgumentParser(description="Import GST master codes into PostgreSQL")
    parser.add_argument("--trigger", default="manual",
                        help="What started this run: manual | cron | api (audit)")
    args = parser.parse_args(argv)

    started = time.time()
    started_at = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime(started))
    trigger = args.trigger
    conn = None
    try:
        log("GST master import started (%s) at %s" % (trigger, started_at))
        conn = psycopg2.connect(cfg.db_dsn())

        prefix = cfg.table_prefix()
        page_tables = {prefix + t: t for t, _, _ in PAGE_TABLES}
        page_tables[prefix + "hsn_master"] = "hsn_master"
        page_tables[prefix + "sac_master"] = "sac_master"
        ensure_schema(conn, sorted(page_tables.keys()))

        summary = {}
        sheet = cfg.master_sheet()
        if sheet:
            log("MST  using master workbook %s" % sheet)
            page, hsn, sac, rates = load_master_sheet(sheet)
        else:
            page = {}
            try:
                page = fetch_master_codes()
            except Exception as exc:  # noqa: BLE001 - GSTN blocks non-India hosts
                log("WARN  Master Codes page unreachable: %s: %s" % (type(exc).__name__, exc))
                log("WARN  page tables (state/country/currency/port/uqc) left unchanged")
                page = {}
                summary["page_fetch"] = {"status": "unreachable",
                                         "error": "%s: %s" % (type(exc).__name__, exc)[:4000]}
            hsn, sac = load_hsn_sac()
            rates = load_rates()

        summary = {}
        # Code-list tables carry no rate by nature - gst_rate/effective_date stay null.
        for filename, records in page.items():
            table = prefix + filename
            if not records:
                summary[table] = {"source": "page", "inserted": 0, "updated": 0}
                continue
            rows = [(code, desc, None, None) for code, desc in records]
            ins, upd = upsert(conn, table, rows)
            summary[table] = {"source": "page", "inserted": ins, "updated": upd}

        def enriched(table, records):
            return [(code, desc, rates.get(code, (None, None))[0],
                     rates.get(code, (None, None))[1]) for code, desc in records]

        for table, records in ((prefix + "hsn_master", hsn), (prefix + "sac_master", sac)):
            if not records:
                summary[table] = {"source": "seed", "inserted": 0, "updated": 0}
                continue
            ins, upd = upsert(conn, table, enriched(table, records))
            summary[table] = {"source": "seed", "inserted": ins, "updated": upd}

        with conn.cursor() as cur:
            cur.execute(
                "INSERT INTO gst_importer_import_logs (run_started_at, run_finished_at, status, trigger, summary) "
                "VALUES (to_timestamp(%s), now(), 'SUCCESS', %s, %s) RETURNING id",
                (started, trigger, json.dumps(summary, default=str)),
            )
            log_id = cur.fetchone()[0]
        conn.commit()

        elapsed = time.time() - started
        log("")
        log("%-28s %9s %9s" % ("table", "inserted", "updated"))
        for table, s in sorted(summary.items()):
            if "inserted" in s:
                log("%-28s %9d %9d" % (table, s["inserted"], s["updated"]))
            else:
                log("%-28s %s" % (table, s))
        log("audit log row id: %s" % log_id)
        log("RESULT status=SUCCESS duration=%.1fs" % elapsed)
        return 0
    except Exception as exc:  # noqa: BLE001 - every failure is reported and logged
        elapsed = time.time() - started
        detail = "%s: %s" % (type(exc).__name__, exc)
        log("ERROR %s" % detail)
        if conn is not None:
            try:
                with conn.cursor() as cur:
                    cur.execute(
                        "INSERT INTO gst_importer_import_logs (run_started_at, run_finished_at, status, trigger, error_detail) "
                        "VALUES (to_timestamp(%s), now(), 'FAILED', %s, %s)",
                        (started, trigger, detail[:4000]),
                    )
                conn.commit()
            except Exception:  # noqa: BLE001 - logging failure must not mask the original
                pass
        log("RESULT status=FAILED duration=%.1fs" % elapsed)
        return 1
    finally:
        if conn is not None:
            conn.close()


if __name__ == "__main__":
    sys.exit(main())
