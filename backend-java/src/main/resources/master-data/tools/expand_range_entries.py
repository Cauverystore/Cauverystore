# -*- coding: utf-8 -*-
"""
Expands rate entries the extraction collapsed to the first heading of a range.

The bug
-------
CBIC writes a single schedule entry against a span of headings: "5208 to 5212 | Woven fabrics
of cotton | 2.5%". The extractor read the code cell as "5208" and left the rest of the span in
the description, so the seed carried one row for 5208 and nothing for 5209-5212. The tell is a
description that still begins "to NNNN".

Why it matters
--------------
The resolver walks 8 -> 6 -> 4 -> 2. A cotton lungi is 52095110, and with 5209 missing the walk
falls past it to chapter 52, whose only row is the *Gandhi Topi and khadi yarn* exemption at
nil. So a lungi was being invoiced at 0% instead of 5%. Twelve polymer headings (3902-3913)
were falling to a chapter-39 row published for paper sacks, charging 5% where 18% is due.

What this does
--------------
For every row whose description begins "to NNNN", writes the same rate to every heading in the
span and restores the description to what the notification actually says. Only 4-digit spans
are touched: "50 to 55 Khadi fabric, sold through KVIC" is a chapter span carrying a condition
this file cannot express, and expanding it would exempt all of chapters 50-55.

Run from backend-java:
    python src/main/resources/master-data/tools/expand_range_entries.py
"""

import copy
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, "..", "gst_rate_seed.json")

RANGE_RE = re.compile(r"^\s*to\s+(\d{4})\s+(.*)$", re.DOTALL)

# A span wider than this in one entry is more likely a parse artefact than a real schedule
# entry, and is reported rather than written.
MAX_SPAN = 20


def main():
    with open(SEED, encoding="utf-8") as fh:
        rows = json.load(fh)

    existing = {r["hsnCode"] for r in rows}
    added, fixed, skipped = [], 0, []

    for row in list(rows):
        code = row["hsnCode"]
        text = row.get("conditionText") or ""
        if len(code) != 4 or not code.isdigit():
            continue
        m = RANGE_RE.match(text)
        if not m:
            continue

        end, description = m.group(1), m.group(2).strip()
        start = int(code)
        if int(end) <= start or int(end) - start > MAX_SPAN:
            skipped.append((code, end, "implausible span"))
            continue

        # The description belongs to the whole span, so the first heading gets it back too.
        row["conditionText"] = description
        fixed += 1

        for n in range(start + 1, int(end) + 1):
            heading = "%04d" % n
            if heading in existing:
                continue          # never overwrite a heading the seed already prices
            clone = copy.deepcopy(row)
            clone["hsnCode"] = heading
            clone["conditionText"] = description
            clone["notes"] = ((clone.get("notes") or "").strip() + " | " if clone.get("notes")
                              else "") + (
                "Written from the schedule entry '%s to %s', which the extraction had "
                "collapsed onto %s alone. Without this the heading fell through to its "
                "chapter and took a rate published for other goods." % (code, end, code))
            rows.append(clone)
            existing.add(heading)
            added.append(heading)

    rows.sort(key=lambda r: (r["hsnCode"], r.get("gstRate") or 0))
    with open(SEED, "w", encoding="utf-8") as fh:
        json.dump(rows, fh, indent=2, ensure_ascii=False)

    print("Restored %d range descriptions; wrote %d missing headings." % (fixed, len(added)))
    print("Added: %s" % ", ".join(added))
    for code, end, why in skipped:
        print("Skipped %s to %s: %s" % (code, end, why))
    return 0


if __name__ == "__main__":
    sys.exit(main())
