# -*- coding: utf-8 -*-
"""
Downloads the e-invoice portal's master code lists into one file per classification.

Source: https://einvoice1.gst.gov.in/Others/MasterCodes

These are the code lists the IRP validates an e-invoice against. Sending a country, port,
currency or unit code that is not on these lists gets the invoice rejected, so they are worth
holding locally rather than guessing at.

The page is server-rendered, so a plain fetch is enough - no browser, no API key. Six tables
come down, and each is written to its own file:

    state_master.json     40 GST state codes
    country_master.json   ISO country codes as the IRP accepts them
    currency_master.json  ISO currency codes
    port_master.json      Indian port / ICD / SEZ codes
    unit_master.json      UQC codes - the unit of measure on every invoice line
    pincode_state_map.json  which pincode prefixes belong to which state

State codes are padded to two digits. The portal prints them unpadded ("1" for Jammu and
Kashmir) but a GSTIN carries them padded, and comparing "1" against "01" is how a place-of-
supply check silently fails.

Run from backend-java:
    python src/main/resources/master-data/tools/fetch_einvoice_master_codes.py
"""

import io
import json
import os
import re
import sys
import urllib.request

URL = "https://einvoice1.gst.gov.in/Others/MasterCodes"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")

# Table order on the page. Checked against the header row so a reordering is caught rather
# than silently writing ports into the currency file.
TABLES = [
    ("state_master.json",      ["state code", "state name"],                 ["code", "name"]),
    ("country_master.json",    ["country code", "country name"],             ["code", "name"]),
    ("currency_master.json",   ["currency code", "currency name"],           ["code", "name"]),
    ("port_master.json",       ["port code", "port name"],                   ["code", "name"]),
    ("unit_master.json",       ["unit code", "unit description"],            ["code", "description"]),
    ("pincode_state_map.json", ["state code", "state name", "range"],        ["stateCode", "stateName", "pincodeRange"]),
]

TAG_RE = re.compile(r"<[^>]+>")


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=90) as resp:
        return resp.read().decode("utf-8", errors="replace")


def clean(cell):
    text = TAG_RE.sub("", cell)
    text = (text.replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
                .replace("&quot;", '"'))
    return re.sub(r"\s+", " ", text).strip()


def parse_tables(html):
    """Every table on the page as a list of rows, each row a list of cell strings."""
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
    """Loose match - the portal's wording shifts, the column meaning does not."""
    joined = " ".join(header).lower()
    return all(any(word in joined for word in exp.split()) for exp in expected)


def main():
    html = fetch(URL)
    tables = parse_tables(html)
    if len(tables) < len(TABLES):
        print("Expected %d tables, found %d - the page layout has changed. Nothing written."
              % (len(TABLES), len(tables)))
        return 1

    written = []
    for idx, (filename, expected_header, keys) in enumerate(TABLES):
        rows = tables[idx]
        header, body = rows[0], rows[1:]
        if not header_matches(header, expected_header):
            print("Table %d header is %r, expected something like %r - refusing to write %s."
                  % (idx, header, expected_header, filename))
            return 1

        records = []
        for cells in body:
            if len(cells) < len(keys):
                continue
            record = dict(zip(keys, cells[:len(keys)]))
            # A GSTIN carries the state code padded to two digits; the portal prints it
            # unpadded. Comparing "1" against "01" is how a place-of-supply check fails
            # without anyone noticing.
            for field in ("code", "stateCode"):
                if field in record and record[field].isdigit():
                    record[field] = record[field].zfill(2)
            records.append(record)

        path = os.path.join(OUT_DIR, filename)
        with io.open(path, "w", encoding="utf-8") as fh:
            json.dump(records, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        written.append((filename, len(records)))

    print("Written to %s:" % os.path.normpath(OUT_DIR))
    for filename, count in written:
        print("  %-24s %5d" % (filename, count))
    return 0


if __name__ == "__main__":
    sys.exit(main())
