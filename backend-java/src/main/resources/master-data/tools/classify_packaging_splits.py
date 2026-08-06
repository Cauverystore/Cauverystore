"""
Marks the GST rate rows whose split is purely about packaging.

Staples are taxed on how they are sold rather than on what they are: rice under 1006 is 5%
pre-packaged and labelled and nil loose. The HSN code is the same either way, so the resolver
cannot choose without knowing the packaging - which is why these headings otherwise sit in the
review backlog forever, taxing live products at the fallback rate.

The test applied here is deliberately strict. A heading qualifies only when removing the
packaging clause from both descriptions leaves them IDENTICAL, which proves packaging is the
only thing separating them. Headings that also turn on some other property keep needing a human:

    1006  "Rice, pre-packaged and labelled"                              -> "rice,"
          "Rice, other than pre-packaged and labelled"                   -> "rice,"
          identical, so this is a pure packaging split.                     QUALIFIES

    0203  "All goods, other than fresh or chilled, pre-packaged and labelled"
                                                    -> "all goods, other than fresh or chilled"
          "All goods, fresh or chilled"             -> "all goods, fresh or chilled"
          not identical - freshness matters too, and a wrong guess here would tax
          fresh meat as frozen.                                            REJECTED

Run from backend-java:
    python src/main/resources/master-data/tools/classify_packaging_splits.py
"""

import json
import os
import re
import sys
from collections import defaultdict

SEED = os.path.join(os.path.dirname(__file__), "..", "gst_rate_seed.json")

PACKAGED = "PRE_PACKAGED"
UNPACKAGED = "NOT_PRE_PACKAGED"

# "pre -packaged" and "pre- packaged" both occur; the PDF text layer breaks the hyphen.
PACK_RE = re.compile(r"pre\s*-\s*packaged\s+and\s+labell?ed", re.I)
OTHER_THAN_PACK_RE = re.compile(r"other\s+than\s+pre\s*-\s*packaged\s+and\s+labell?ed", re.I)


def norm(text):
    return re.sub(r"\s+", " ", (text or "")).strip().lower()


def strip_packaging(text):
    """Remove the packaging clause and the punctuation left behind by removing it."""
    t = OTHER_THAN_PACK_RE.sub("", text)
    t = PACK_RE.sub("", t)
    t = re.sub(r"[\s,;]+", " ", t)
    t = t.strip(" ,;")
    # The two sides are joined differently - "..., pre-packaged and labelled" against
    # "..., and other than pre-packaged and labelled" - so removing the clause leaves a
    # dangling conjunction on one side only. That is punctuation, not a difference in the
    # goods, and treating it as one wrongly held back headings like 0303 and 0305 where
    # packaging really is the only thing that separates the rates.
    t = re.sub(r"\s+(and|or)$", "", t)
    return t.strip(" ,;")


def side_of(text):
    """Which side of the packaging split a description sits on, or None."""
    if OTHER_THAN_PACK_RE.search(text):
        return UNPACKAGED
    if PACK_RE.search(text):
        return PACKAGED
    return None


def main():
    with open(SEED, encoding="utf-8") as fh:
        rows = json.load(fh)

    by_heading = defaultdict(list)
    for row in rows:
        by_heading[row["hsnCode"]].append(row)

    changed_headings = []
    for hsn, group in by_heading.items():
        if len(group) != 2:
            continue
        if any((r.get("conditionType") or "NONE") != "NONE" for r in group):
            continue  # already carries a condition (a value band); leave it alone

        sides = [side_of(norm(r.get("conditionText"))) for r in group]
        if sorted(filter(None, sides)) != sorted([PACKAGED, UNPACKAGED]):
            continue

        stripped = {strip_packaging(norm(r.get("conditionText"))) for r in group}
        if len(stripped) != 1:
            continue  # something other than packaging also separates them

        # The packaged side must be the dearer one; if not, the descriptions were misread.
        packaged = group[sides.index(PACKAGED)]
        loose = group[sides.index(UNPACKAGED)]
        if packaged["gstRate"] <= loose["gstRate"]:
            print(f"  ! HSN {hsn}: packaged {packaged['gstRate']}% is not above "
                  f"loose {loose['gstRate']}% - skipping, descriptions look misaligned")
            continue

        for row, side in zip(group, sides):
            row["conditionType"] = side
            row["status"] = "VERIFIED"
            row["notes"] = ("Rate depends only on whether the goods are pre-packaged and "
                            "labelled, which the product record states, so the resolver can "
                            "choose without a human.")
        changed_headings.append(hsn)

    with open(SEED, "w", encoding="utf-8") as fh:
        json.dump(rows, fh, indent=2, ensure_ascii=False)
        fh.write("\n")

    print(f"classified {len(changed_headings)} headings as pure packaging splits")
    for hsn in sorted(changed_headings):
        print(f"  {hsn}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
