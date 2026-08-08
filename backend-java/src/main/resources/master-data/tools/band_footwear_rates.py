# -*- coding: utf-8 -*-
"""
Puts the sale-value band on the footwear headings, where the resolver will actually reach it.

The bug
-------
Notification 09/2025 taxes footwear of sale value not exceeding Rs 2,500 per pair at 5%, and
everything above it at 18%. The extraction put the 5% carve-out against chapter 64 and the 18%
entries against the headings, 6401 to 6406.

The resolver walks 8 -> 6 -> 4 -> 2 and stops at the first level that answers. A shoe coded
64031990 therefore hits 6403's flat, unconditional 18% and never reaches the chapter, so every
pair of footwear was taxed at 18% - including a Rs 499 pair owing 5%. That is a 13-point
overcharge on the cheapest and most commonly sold footwear there is.

Apparel avoided this by accident: headings 6101-6117 carry no rows at all, so the walk reaches
chapter 61, where both bands sit together and the resolver can choose between them on price.

The fix
-------
Give each footwear heading the same pair apparel has - 5% at or below Rs 2,500 per pair, 18%
above it - so the choice is made at the level the resolver actually stops on. Nothing is
invented: both rates and the threshold are already in the seed, just at levels that could not
meet.

Run from backend-java:
    python src/main/resources/master-data/tools/band_footwear_rates.py
"""

import copy
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, "..", "gst_rate_seed.json")

THRESHOLD = 2500.0
UNIT = "pair"
LOWER_RATE = 5.0
HEADINGS = ["6401", "6402", "6403", "6404", "6405"]

# 6406 is deliberately absent: parts of footwear - uppers, soles, heels - are not "footwear of
# sale value not exceeding Rs 2500 per pair", which is priced per pair of finished footwear.
# They stay at 18% flat.


def main():
    with open(SEED, encoding="utf-8") as fh:
        rows = json.load(fh)

    by_code = {}
    for row in rows:
        by_code.setdefault(row["hsnCode"], []).append(row)

    changed, added = [], []
    for heading in HEADINGS:
        existing = by_code.get(heading, [])
        flat = [r for r in existing
                if r.get("conditionType", "NONE") in (None, "", "NONE") and r["gstRate"] == 18.0]
        if not flat:
            print("Skipped %s: no flat 18%% row to band." % heading)
            continue
        if any(r.get("conditionType") in ("VALUE_UPTO", "VALUE_ABOVE") for r in existing):
            print("Skipped %s: already banded." % heading)
            continue

        upper = flat[0]
        description = upper.get("conditionText") or "Footwear"

        # The existing 18% row becomes the upper band rather than a new row, so its provenance
        # and any admin edits carry over instead of being duplicated.
        upper["conditionType"] = "VALUE_ABOVE"
        upper["thresholdAmount"] = THRESHOLD
        upper["thresholdUnit"] = UNIT
        upper["notes"] = ((upper.get("notes") or "").strip() + " | " if upper.get("notes") else "") + (
            "Banded on %s: this heading's rate applies above Rs %d per pair. Left flat it "
            "answered before the chapter-level carve-out the resolver never reached, taxing "
            "every pair at 18%% including those owing 5%%." % (heading, THRESHOLD))
        changed.append(heading)

        lower = copy.deepcopy(upper)
        lower["gstRate"] = LOWER_RATE
        lower["conditionType"] = "VALUE_UPTO"
        lower["thresholdAmount"] = THRESHOLD
        lower["thresholdUnit"] = UNIT
        lower["conditionText"] = (
            "Footwear of sale value not exceeding Rs 2500 per pair - " + description)
        lower["notes"] = (
            "Written from the chapter 64 carve-out in Notification 09/2025, which taxes footwear "
            "of sale value not exceeding Rs 2500 per pair at 5%. It sat against the chapter, "
            "where the resolver never reached it because this heading answered first.")
        rows.append(lower)
        added.append(heading)

    rows.sort(key=lambda r: (r["hsnCode"], r.get("conditionType") or "", r.get("gstRate") or 0))
    with open(SEED, "w", encoding="utf-8") as fh:
        json.dump(rows, fh, indent=2, ensure_ascii=False)

    print("Banded %d heading(s) as VALUE_ABOVE: %s" % (len(changed), ", ".join(changed)))
    print("Added %d lower band(s) at %s%%: %s" % (len(added), LOWER_RATE, ", ".join(added)))
    print("6406 (parts of footwear) left flat at 18%% - parts are not sold per pair.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
