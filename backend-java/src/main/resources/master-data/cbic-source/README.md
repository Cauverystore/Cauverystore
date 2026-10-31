# CBIC source notifications

The gazette notifications the GST rates are taken from, committed so every rate the store
charges can be traced back to law rather than to anyone's recollection. Downloaded from
`taxinformation.cbic.gov.in`.

Do not edit these. To change a rate, add the notification that changes it and re-derive.

## Rate notifications

| File | Notification | Effective | What it does |
|---|---|---|---|
| `09-2025-CTR-eng.pdf` | 09/2025-Central Tax (Rate), 17-09-2025 | 22-09-2025 | The base rate schedule. Supersedes 01/2017. Schedules I–VII. |
| `10-2025-CTR-eng.pdf` | 10/2025-Central Tax (Rate), 17-09-2025 | 22-09-2025 | The exemption list — everything at nil. Supersedes 02/2017. |
| `19-2025-CTR-Eng.pdf` | 19/2025-Central Tax (Rate), 31-12-2025 | 01-02-2026 | Amends 09/2025: pan masala and tobacco to 40%, biris added at 18%, **Schedule VII omitted entirely**. |
| `CTR-E-updated.pdf` | 01/2026-Central Tax (Rate), 30-04-2026 | 01-05-2026 | Amends 09/2025: re-cuts the 2202 beverage headings into eight-digit codes at 5% and 40%. |
| `02-2026-CTR-eng.pdf` | 02/2026-Central Tax (Rate), 27-10-2026 | 01-11-2026 | Amends 09/2025 (57th GST Council): retreaded tractor tyres (4012) to 5%, other 4012 goods stay 18%; reworded S.No 237 (3101 organic fertilisers, 5%) to include sea-weed bio-stimulants. |
| `03-2026-CTR-eng.pdf` | 03/2026-Central Tax (Rate), 27-10-2026 | 01-11-2026 | Amends 10/2025: psyllium seeds (1211 90 13) exempt. The IGST twin prints the code as 1211 90. |

### The rest of the 28 October 2026 set

Notifications 04 to 10/2026-Central Tax (Rate), issued the same day and also effective
01-11-2026, change no goods rate in this seed. They are rules, not rates, and are recorded
here so nobody has to re-read them to find that out:

- **04/2026** (amends 4/2017, reverse charge on goods): a registered buyer pays the tax when
  buying used cooking oil (1518), plastic waste and scrap (3915), tyre waste and scrap (4004)
  or e-waste (8549) from an unregistered person.
- **05/2026** (amends 26/2018): new meaning of "Nominated Agency" (DGFT Handbook Appendix 4B).
- **06/2026** (amends 11/2017, services rates): **delivery services supplied through an
  e-commerce operator by an unregistered person, or delivering goods sold through an e-commerce
  operator, are 5% without input tax credit** (S.No 12(iii)); EV passenger transport and EV
  renting with battery charging at 5% (or 18%); input tax credit limits for hotel, restaurant
  and outdoor catering suppliers buying from their own trade. Store invoices are unaffected
  because a seller's delivery charge is taxed with the goods (composite supply), but delivery
  partners' bills to sellers and to the store should now show 5%.
- **07/2026** (amends 12/2017, services exemptions): the goods-transport-agency exemption no
  longer covers goods sold through an e-commerce operator; storage of seeds for sowing added;
  new nil entries for helicopter seat-share flights in the North East, Seamen's Provident Fund,
  toll-collection rights granted by government, and coffee curing.
- **08/2026** (amends 13/2017, services reverse charge): S.No 15 figure changed from 6 to 9.
- **09/2026** (amends 17/2017, section 9(5)): **the e-commerce operator pays the tax on
  delivery services (other than postal and courier) supplied through it by people who are not
  required to register.** Matters if the store ever lets independent riders deliver through it.
- **10/2026** (new, section 148): special procedure for toll-road concessionaires under the
  Toll-Operate-Transfer model.

The Integrated and Union Territory Tax (Rate) notifications of the same numbers mirror these.

### Central Tax (rule) notifications of 26-28 October 2026

Not rate notifications; summarised so the "GST rule changes" panel can be cleared quickly.
Read from the CBIC PDFs on 2026-11-01. Have a CA confirm before acting.

- **03/2026-CT**: brings sections 153 to 155 of the Finance Act, 2026 into force on 01-11-2026.
- **04/2026-CT** (from 01-01-2027): e-invoicing now also covers reverse-charge purchases from
  unregistered persons and import of services, for businesses that must e-invoice.
- **05/2026-CT**: CGST (Amendment) Rules, 2026, mostly from 01-11-2026 (some parts from
  01-12-2026, 01-01-2027 and 01-04-2027). Long; mostly registration forms and procedure, refunds,
  returns forms and recovery. The part that concerns a marketplace is **new rule 14B**: a seller
  registered in one State can register in another State using an **e-commerce operator's
  warehouse** as its principal place of business, if its sales there to registered buyers carry no
  more than Rs 2.5 lakh of tax a month. The operator is asked to consent on the GST portal and
  **must answer within seven working days**, or the application lapses; it must appoint an
  authorised representative in each State, and withdrawing consent can cancel the seller's
  registration there. This only matters if Cauvery Store operates warehouses that sellers stock.
- **06/2026-CT**: suppliers of used cooking oil (1518), plastic scrap (3915), tyre scrap (4004),
  metal scrap (chapters 72-81) and e-waste (8549) must register whatever their turnover.
- **07/2026-CT**: a registered buyer of those same scrap goods from a registered seller must
  deduct TDS under section 51.

The Taxscan article of 28-10-2026 ("Big GST Changes from Nov 1") covers the same package; its
headline items (fertilisers, tractor tyres, psyllium, waste, registration, TDS) all trace to the
notifications above.

Schedule rates are **CGST halves** — the customer-facing total is double. Schedule I at 2.5%
is 5% GST.

## Not a rate notification

| File | What it actually is |
|---|---|
| `central-tax-02-gst-10062026.pdf` | Notification 02/2026-**Central Tax** (not "Central Tax (Rate)"), 07-05-2026. Empowers the GST Appellate Tribunal's Principal Bench to hear appeals under section 101B. Sets no rates and affects no HSN code. |

It is kept here for provenance, but nothing should parse it for rates. Its presence in this
folder is the only thing that suggests otherwise, which is why this note exists.

## Schedule VII is gone

Notification 19/2025 clause (c) omitted Schedule VII (14% CGST = 28%) outright from
01-02-2026 and moved its contents into Schedule III at 40%. There is no 28% slab under GST 2.0
and the seed carries no 28% rows. `tools/parse_cbic_notifications.py` deliberately does not
parse Schedule VII from the base notification — doing so would resurrect descriptions for a
rate that no longer applies to anything.

## How these become rates

`tools/parse_cbic_notifications.py` reads the two **base** notifications (09 and 10) into an
authoritative code → (rate, description) map. It does not read the amendments; their rows are
applied to `gst_rate_seed.json` directly and carry the later `effectiveFrom` dates
(2026-02-01, 2026-05-01), so those legitimately will not match the parser's map.

### Ending a rate

Each seed row carries `effectiveTo` — `null` while the rate is still in force, otherwise the
last day it applied. A rate is never deleted when a notification ends it, because an invoice
raised while it was live still has to reprint at it.

Set it whenever an amendment **substitutes** an entry rather than adding one. Notification
01/2026 replaced the eight-digit beverage codes at Schedule I S.No 150/151 and Schedule III
S.No 3, so `2202 99 20`, `2202 99 30` and `2202 99 90` end on 2026-04-30 and their finer
replacements start the next day. Left open they would have gone on resolving alongside the
codes that replaced them.

Do **not** set it for a carve-out. Notification 19/2025 moved 2401 to 40% but excluded tobacco
leaves, which stay at 5% — two live rates on one heading, not a supersession, which is why
2401 correctly has two open rows and needs a human to say which applies.

Extraction uses `pdftotext -table`, which rebuilds the real cell grid. An earlier pass used
`-layout`, which guesses column positions and slipped on wrapped cells — the exemption list's
first entry came out as a blank code against "Live asses, mules and hinnies", shifting every
code below it by one row and attaching descriptions to their neighbours. If these files are
ever re-parsed, check the spot-checks the parser prints before trusting the output.
