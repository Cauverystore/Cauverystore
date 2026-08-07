# -*- coding: utf-8 -*-
"""
Derives the HSN chapter master from the chapter titles already carried in hsn_master.json.

Why this exists
---------------
CBIC publishes some GST rates against a whole chapter rather than a heading - "Chapter 61,
articles of apparel and clothing accessories, knitted or crocheted". 36 such chapter codes sit
in gst_rate_seed.json. They match nothing in hsn_master.json, which holds only 4-, 6- and
8-digit rows, because an invoice never carries a 2-digit HSN. Both files are right; they sit at
different levels of one tree, and GstRateResolver already bridges them by walking 8 -> 6 -> 4
-> 2. So nothing is mis-rated.

What is missing is a name. Without one, a chapter can only ever be shown to a seller as "61",
and a validator can only compare codes for equality and report 36 false orphans.

Where the titles come from
--------------------------
Not from anywhere new. The e-invoice bundle already embeds the Customs Tariff chapter title on
3,146 of its own rows, as a "TITLE~heading description" prefix. So this reads the official
master rather than introducing a second source that could disagree with it.

Why a vote
----------
Three chapters carry a stray title on a single row - chapter 75 (nickel) has one row tagged
"IRON AND STEEL", which is chapter 72's title; chapter 83 has one tagged with chapter 85's;
chapter 38 has one blank. These are defects in the source bundle, so the title is taken by
majority and the vote has to be decisive: a chapter whose rows genuinely disagree is a fact
about the data worth stopping for, not something to resolve by picking the larger pile.

The five chapters with no title
-------------------------------
04, 34, 35, 80 and 99 carry no ~ prefix on any row. They are written out with a null title
rather than a guessed one. Every chapter the rate seed actually uses has a title, so this
costs nothing today; inventing five would put unsourced text into compliance data, which is
the one thing this whole exercise exists to avoid.

Run from backend-java:
    python src/main/resources/master-data/tools/derive_chapter_master.py
"""

import collections
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..")

# A title needs this share of the rows that carry one, and this many times the runner-up,
# before it is taken as the chapter's. Below either, the disagreement is real and is raised.
MIN_SHARE = 0.80
MIN_RATIO = 3


def load(name):
    with open(os.path.join(DATA, name), encoding="utf-8") as fh:
        return json.load(fh)


def main():
    hsn = load("hsn_master.json")
    seed = load("gst_rate_seed.json")

    votes = collections.defaultdict(collections.Counter)
    for row in hsn:
        code, desc = row.get("code", ""), row.get("description", "")
        if len(code) < 2 or "~" not in desc:
            continue
        title = desc.split("~", 1)[0].strip()
        if title:
            votes[code[:2]][title] += 1

    chapters, disputed, untitled = [], [], []
    for chapter in sorted({r["code"][:2] for r in hsn if len(r.get("code", "")) >= 2}):
        counted = votes.get(chapter)
        if not counted:
            untitled.append(chapter)
            chapters.append({"chapter": chapter, "title": None,
                             "titleSource": None, "rowsAgreeing": 0})
            continue

        ranked = counted.most_common()
        title, top = ranked[0]
        runner_up = ranked[1][1] if len(ranked) > 1 else 0
        total = sum(counted.values())
        if top / total < MIN_SHARE or (runner_up and top < runner_up * MIN_RATIO):
            disputed.append((chapter, ranked))
            continue

        chapters.append({
            "chapter": chapter,
            "title": title,
            "titleSource": "e-invoice HSN master, chapter title prefix",
            "rowsAgreeing": top,
        })

    if disputed:
        print("Chapters whose rows genuinely disagree on a title - resolve before writing:")
        for chapter, ranked in disputed:
            print("  %s  %s" % (chapter, ranked))
        return 1

    # Every chapter the rate seed prices must be nameable, or the exercise has not paid off.
    seed_chapters = sorted({r["hsnCode"] for r in seed if len(r["hsnCode"]) == 2})
    named = {c["chapter"] for c in chapters if c["title"]}
    unnameable = [c for c in seed_chapters if c not in named]
    if unnameable:
        print("Rate-seed chapters that would still have no title: %s" % unnameable)
        return 1

    out = os.path.join(DATA, "chapter_master.json")
    with open(out, "w", encoding="utf-8") as fh:
        json.dump(chapters, fh, indent=2, ensure_ascii=False)

    print("Wrote %d chapters (%d titled, %d without a published title: %s)"
          % (len(chapters), len(named), len(untitled), ", ".join(untitled)))
    print("All %d chapters priced by the rate seed are named." % len(seed_chapters))
    return 0


if __name__ == "__main__":
    sys.exit(main())
