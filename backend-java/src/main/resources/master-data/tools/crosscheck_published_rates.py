# -*- coding: utf-8 -*-
"""
Checks the committed rate seed against a rate list published elsewhere.

What this is for
----------------
The seed is derived from the CBIC notifications and nothing here changes that. A second list,
compiled independently by somebody else reading the same notifications, is still worth having:
where it agrees, that is corroboration, and where it disagrees, one of the two is wrong and it
is worth knowing which before a customer is charged.

What it deliberately does not do
--------------------------------
It does not write to the seed. A summary table on a consultancy's website cites no notification
and carries no date, so it cannot settle anything - it can only raise a question for a human to
answer against the notification itself. Every disagreement below is a lead, not a correction.

Two kinds of disagreement mean different things:

  MISMATCH   The seed and the list give different rates for the same goods. One is wrong.
  FLATTENED  The list gives one rate where the seed gives several. Usually the list has
             summarised away a condition CBIC actually publishes - a price band, a packaging
             split - in which case the seed is the better of the two. Worth reading, not
             worth acting on blindly.

Run from backend-java:
    python src/main/resources/master-data/tools/crosscheck_published_rates.py
"""

import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SEED = os.path.join(HERE, "..", "gst_rate_seed.json")

# Transcribed from mundhraconsulting.com/gst/new-gst-rate-list-2026 on 2026-08-08.
# (code, what it says the goods are, the rate it publishes)
PUBLISHED = [
    ("0401", "UHT milk", 0.0),
    ("0406", "Chena / paneer, pre-packaged", 0.0),
    ("1905", "Pizza bread, khakhra, chapathi, roti", 0.0),
    ("2106", "Paratha, parotta, Indian breads", 0.0),
    ("3305", "Hair oil, shampoo", 5.0),
    ("3306", "Toothpaste, dental floss", 5.0),
    ("3401", "Toilet soap", 5.0),
    ("8712", "Bicycles, non-motorised", 5.0),
    ("8701", "Tractors", 5.0),
    ("8433", "Harvesting / threshing machinery", 5.0),
    ("2807", "Sulphuric acid", 5.0),
    ("2814", "Ammonia", 5.0),
    ("7321", "Kerosene and wood-burning stoves", 5.0),
    ("6911", "Porcelain / china tableware", 5.0),
    ("1902", "Pasta, noodles, macaroni", 5.0),
    ("1806", "Chocolates, cocoa preparations", 5.0),
    ("2101", "Coffee extracts and essences", 5.0),
    ("8415", "Air conditioners", 18.0),
    ("8422", "Dishwashing machines", 18.0),
    ("8528", "Television sets", 18.0),
    ("8703", "Small cars", 18.0),
    ("8711", "Motorcycles up to 350cc", 18.0),
    ("8708", "Auto parts", 18.0),
    ("2523", "Cement", 18.0),
    ("4011", "New pneumatic tyres", 18.0),
    ("2403", "Pan masala, gutkha, chewing tobacco", 40.0),
    ("2202", "Aerated and carbonated drinks", 40.0),
]

# Where the list itself states a condition, it is recorded so agreement can be judged on the
# band rather than on a single number the seed deliberately does not carry.
PUBLISHED_BANDED = [
    ("61", "Apparel, knitted, per piece", 2500.0, 5.0, 18.0),
    ("62", "Apparel, not knitted, per piece", 2500.0, 5.0, 18.0),
    ("64", "Footwear, per pair", 2500.0, 5.0, 18.0),
]


def load_seed():
    with open(SEED, encoding="utf-8") as fh:
        rows = json.load(fh)
    by_code = {}
    for r in rows:
        by_code.setdefault(r["hsnCode"], []).append(r)
    return by_code


def main():
    seed = load_seed()
    agree, mismatch, flattened, absent = [], [], [], []

    for code, what, published_rate in PUBLISHED:
        rows = seed.get(code)
        if not rows:
            absent.append((code, what, published_rate))
            continue
        rates = sorted({r["gstRate"] for r in rows})
        if rates == [published_rate]:
            agree.append((code, what, published_rate))
        elif published_rate in rates:
            flattened.append((code, what, published_rate, rates,
                              [r["conditionText"][:60] for r in rows]))
        else:
            mismatch.append((code, what, published_rate, rates))

    banded_ok, banded_bad = [], []
    for code, what, threshold, lower, upper in PUBLISHED_BANDED:
        # A band may sit on the chapter or on each heading beneath it; both are correct, so
        # look wherever the resolver would.
        candidates = [c for c in seed if c == code or (c.startswith(code) and len(c) == 4)]
        found_lower = found_upper = False
        for c in candidates:
            for r in seed[c]:
                if r.get("conditionType") == "VALUE_UPTO" and r.get("thresholdAmount") == threshold \
                        and r["gstRate"] == lower:
                    found_lower = True
                if r.get("conditionType") == "VALUE_ABOVE" and r.get("thresholdAmount") == threshold \
                        and r["gstRate"] == upper:
                    found_upper = True
        (banded_ok if (found_lower and found_upper) else banded_bad).append(
            (code, what, threshold, lower, upper, found_lower, found_upper))

    print("=" * 78)
    print("Cross-check of the committed seed against a separately published rate list")
    print("=" * 78)
    print("\nAGREES (%d of %d single-rate entries)" % (len(agree), len(PUBLISHED)))
    for code, what, rate in agree:
        print("   %-6s %-42s %s%%" % (code, what[:42], rate))

    print("\nMISMATCH - one of the two is wrong, resolve against the notification (%d)"
          % len(mismatch))
    for code, what, published_rate, rates in mismatch:
        print("   %-6s %-42s list says %s%%, seed has %s" % (code, what[:42], published_rate, rates))
    if not mismatch:
        print("   none")

    print("\nFLATTENED - the seed carries a distinction the list summarised away (%d)"
          % len(flattened))
    for code, what, published_rate, rates, texts in flattened:
        print("   %-6s %-42s list says %s%%, seed has %s" % (code, what[:42], published_rate, rates))
        for t in texts[:3]:
            print("          - %s" % t)

    print("\nNOT IN THE SEED AT ALL (%d)" % len(absent))
    for code, what, rate in absent:
        print("   %-6s %-42s list says %s%%" % (code, what[:42], rate))
    if not absent:
        print("   none")

    print("\nVALUE-BANDED GOODS")
    for code, what, threshold, lower, upper, lo, up in banded_ok:
        print("   %-4s %-40s both bands present (%s%% / %s%% at Rs %d)"
              % (code, what[:40], lower, upper, threshold))
    for code, what, threshold, lower, upper, lo, up in banded_bad:
        print("   %-4s %-40s INCOMPLETE - lower %s, upper %s"
              % (code, what[:40], "found" if lo else "MISSING", "found" if up else "MISSING"))

    print("\nNothing here has been written to the seed. Each mismatch is a question for whoever")
    print("reads the notification, not an instruction to change a rate.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
