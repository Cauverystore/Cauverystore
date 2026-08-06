"""
Reads the committed CBIC notification PDFs into an authoritative HSN -> (rate, description) map.

Why this exists: the rate seed was first built from `pdftotext -layout`, which lines columns up
by guesswork. Where a table cell wraps onto several lines that guess slips, and the HSN column
drifts out of step with the description column - so entry 1 came out as a blank code against
"Live asses, mules and hinnies" and every code below it was shifted by one row. Descriptions
ended up attached to neighbouring codes, and some were cut off mid-phrase.

`pdftotext -table` reconstructs the actual cell grid instead, which fixes the drift. This script
parses that output so the seed can be checked and repaired against it.

Sources (committed under master-data/cbic-source/):
  09-2025-CTR-eng.pdf  Schedules I-VII, the taxable rates. Rates are CGST halves, so the
                       total GST is double the schedule rate (Schedule I 2.5% -> 5%).
  10-2025-CTR-eng.pdf  The exemption list, i.e. everything at nil.

Usage:
    python parse_cbic_notifications.py            # print a summary
    python parse_cbic_notifications.py --json OUT # write the parsed entries
"""

import argparse
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE_DIR = os.path.join(HERE, "..", "cbic-source")

EXEMPTION_PDF = "10-2025-CTR-eng.pdf"
SCHEDULE_PDF = "09-2025-CTR-eng.pdf"

# Schedule rates are CGST; the customer-facing total is twice that.
SCHEDULE_TOTAL_RATE = {
    "I": 5.0, "II": 18.0, "III": 40.0, "IV": 3.0,
    "V": 0.25, "VI": 1.5, "VII": 28.0,
}

ENTRY_RE = re.compile(r"^\s*(\d+)\.\s+(.*)$")
SCHEDULE_HDR_RE = re.compile(r"Schedule\s+(VII|VI|IV|V|III|II|I)\s*[^\w]*\s*[\d.]+\s*%", re.I)

def pdf_to_table_text(pdf_path):
    """Extract with -table, which rebuilds the cell grid rather than guessing at columns."""
    out = os.path.join(tempfile.mkdtemp(), "out.txt")
    subprocess.run(["pdftotext", "-table", pdf_path, out], check=True)
    with open(out, encoding="utf-8", errors="replace") as fh:
        return fh.read()


# A codes cell holds only tariff numbers, spaces and commas - never prose. Splitting on that
# fact is far steadier than splitting at a fixed column: `-table` still nudges the boundary
# around between pages, and a column split lands mid-number, turning "0204, 0205" into a
# stray "4, 0205" that then reads as part of the description.
CODES_PREFIX_RE = re.compile(r"^([\d\s,]*\d[\d\s,]*?)(?=[A-Za-z\[(]|$)")


def split_row(line):
    """Split one physical line into (codes-part, description-part)."""
    stripped = line.strip()
    if not stripped:
        return "", ""
    m = CODES_PREFIX_RE.match(stripped)
    if not m:
        return "", stripped
    codes_part = m.group(1)
    return codes_part.strip(), stripped[m.end(1):].strip()


def parse_entries(text):
    """
    Group the table into entries: {serial, codes:[...], description:str}.

    An entry starts at "N." and absorbs following lines that carry no serial, which are the
    continuations of its wrapped cells.
    """
    lines = [ln.rstrip() for ln in text.splitlines()]

    entries = []
    current = None
    for line in lines:
        if not line.strip():
            continue
        m = ENTRY_RE.match(line)
        if m:
            if current:
                entries.append(current)
            codes_part, desc_part = split_row(re.sub(r"^\s*\d+\.", "", line))
            current = {"serial": int(m.group(1)), "codes_raw": [codes_part],
                       "desc_parts": [desc_part]}
        elif current:
            codes_part, desc_part = split_row(line)
            if codes_part:
                current["codes_raw"].append(codes_part)
            if desc_part:
                current["desc_parts"].append(desc_part)
    if current:
        entries.append(current)

    for e in entries:
        e["description"] = re.sub(r"\s+", " ", " ".join(e["desc_parts"])).strip()
        e["codes"] = expand_codes(" ".join(e["codes_raw"]))
        del e["desc_parts"], e["codes_raw"]
    return entries


def expand_codes(raw):
    """Pull the tariff codes out of a codes cell, ignoring prose like 'any chapter'."""
    raw = re.sub(r"\s+", " ", raw or "").strip()
    codes = []
    for token in re.findall(r"\d[\d\s]*", raw):
        digits = re.sub(r"\s+", "", token)
        # 2/4/6/8 digits are the real tariff levels; anything else is a stray number.
        if len(digits) in (2, 4, 6, 8):
            codes.append(digits)
    return codes


def parse_exemptions():
    text = pdf_to_table_text(os.path.join(SOURCE_DIR, EXEMPTION_PDF))
    out = []
    for e in parse_entries(text):
        if not e["description"]:
            continue
        out.append({"rate": 0.0, "codes": e["codes"], "description": e["description"],
                    "source": "Notification 10/2025-Central Tax (Rate)"})
    return out


def parse_schedules():
    text = pdf_to_table_text(os.path.join(SOURCE_DIR, SCHEDULE_PDF))
    lines = text.splitlines()

    # Bound each schedule by the next schedule header. Getting this wrong is how an earlier
    # parse let one schedule swallow the rest of the document and taxed meat at 28%.
    marks = []
    for i, line in enumerate(lines):
        m = SCHEDULE_HDR_RE.search(line)
        if m:
            roman = m.group(1).upper()
            if not marks or marks[-1][1] != roman:
                marks.append((i, roman))

    out = []
    for idx, (start, roman) in enumerate(marks):
        end = marks[idx + 1][0] if idx + 1 < len(marks) else len(lines)
        rate = SCHEDULE_TOTAL_RATE.get(roman)
        if rate is None:
            continue
        for e in parse_entries("\n".join(lines[start:end])):
            if not e["description"]:
                continue
            out.append({"rate": rate, "codes": e["codes"], "description": e["description"],
                        "source": f"Notification 09/2025-Central Tax (Rate), Schedule {roman}"})
    return out


def build():
    return parse_exemptions() + parse_schedules()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--json", help="write parsed entries here")
    args = ap.parse_args()

    entries = build()
    exempt = [e for e in entries if e["rate"] == 0.0]
    print(f"parsed {len(entries)} entries "
          f"({len(exempt)} exempt, {len(entries) - len(exempt)} taxable)")
    print(f"distinct codes: {len({c for e in entries for c in e['codes']})}")

    # Spot-checks against facts that are easy to verify by eye, so a silent parser
    # regression shows up here rather than in the tax charged.
    checks = [("0101", 0.0, "asses"), ("1006", 0.0, "rice"), ("0406", 0.0, "paneer")]
    for code, rate, needle in checks:
        hits = [e for e in entries
                if code in e["codes"] and e["rate"] == rate and needle in e["description"].lower()]
        print(f"  check {code} @ {rate}% mentions '{needle}': {'OK' if hits else 'MISSING'}")

    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(entries, fh, indent=2, ensure_ascii=False)
        print(f"wrote {args.json}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
