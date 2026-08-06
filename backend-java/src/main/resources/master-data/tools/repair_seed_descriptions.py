"""
Repairs the goods descriptions in gst_rate_seed.json against the committed CBIC PDFs.

The seed's descriptions came from a `pdftotext -layout` pass whose column guess slipped on
wrapped table cells. Two things went wrong:

  truncation  a description stopped mid-phrase, e.g. HSN 1212's nil row read
              "Locust beans, seaweeds and other algae, sugar beet and" and simply ended.
  bleed       a description ran on into the following table row, e.g. HSN 01012100's nil row
              read "Live horses 2.5% 2. 0202, 0203, 0204, ...".

Either way a reviewer opening the GST screen cannot tell the competing rates apart, which is
the whole point of showing them. parse_cbic_notifications.py re-reads the PDFs with
`pdftotext -table`, which rebuilds the real cell grid, and this script copies those
descriptions back over the damaged ones.

Deliberately conservative:

  * Only descriptions are touched. Rates, statuses, conditions and effective dates are left
    exactly as they are - changing what a product is taxed at is not a text repair.
  * A row is only rewritten when the notifications give exactly ONE description for that
    (code, rate). Where a code carries several entries at the same rate - 0204 is nil both
    "fresh or chilled" and "other than fresh or chilled, other than pre-packaged" - there is
    no way to tell which row means which, so it is left alone for a human.

Run from backend-java:
    python src/main/resources/master-data/tools/repair_seed_descriptions.py [--dry-run]
"""

import argparse
import json
import os
import re
import sys
from collections import defaultdict

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import parse_cbic_notifications as cbic  # noqa: E402

SEED = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "gst_rate_seed.json")

# A description that swallowed the next table row keeps its rate marker or serial number.
BLEED_RE = re.compile(r"\d\.\d+\s*%|\b\d{1,3}\.\s+\d{4}\b")


def looks_damaged(text):
    text = (text or "").strip()
    if not text:
        return True
    if BLEED_RE.search(text):
        return True
    # cut off mid-phrase: ends on a word that cannot end a description
    if re.search(r"\b(and|or|of|the|other than|including|whether)$", text, re.I):
        return True
    return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    with open(SEED, encoding="utf-8") as fh:
        seed = json.load(fh)

    by_key = defaultdict(set)
    for entry in cbic.build():
        for code in entry["codes"]:
            by_key[(code, entry["rate"])].add(entry["description"])

    repaired, ambiguous, unconfirmed, already_good = 0, 0, 0, 0
    for row in seed:
        key = (row["hsnCode"], row["gstRate"])
        options = by_key.get(key)
        if not options:
            if looks_damaged(row.get("conditionText")):
                unconfirmed += 1
                print(f"  ! HSN {row['hsnCode']} @ {row['gstRate']}% is damaged but the "
                      f"notifications do not carry that rate for that code - needs a human")
            continue
        if len(options) > 1:
            if looks_damaged(row.get("conditionText")):
                ambiguous += 1
                print(f"  ? HSN {row['hsnCode']} @ {row['gstRate']}% is damaged but the code "
                      f"has {len(options)} descriptions at that rate - left for a human")
            continue

        authoritative = next(iter(options))
        current = re.sub(r"\s+", " ", (row.get("conditionText") or "")).strip()
        if current == authoritative:
            already_good += 1
            continue
        row["conditionText"] = authoritative
        repaired += 1

    print(f"\nrewrote {repaired} descriptions from the notifications")
    print(f"  {already_good} already matched")
    print(f"  {ambiguous} damaged but ambiguous - left alone")
    print(f"  {unconfirmed} damaged and unconfirmed - left alone")

    if args.dry_run:
        print("(dry run - nothing written)")
        return 0

    with open(SEED, "w", encoding="utf-8") as fh:
        json.dump(seed, fh, indent=2, ensure_ascii=False)
        fh.write("\n")
    print(f"wrote {os.path.normpath(SEED)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
