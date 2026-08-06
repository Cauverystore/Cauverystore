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

Extraction uses `pdftotext -table`, which rebuilds the real cell grid. An earlier pass used
`-layout`, which guesses column positions and slipped on wrapped cells — the exemption list's
first entry came out as a blank code against "Live asses, mules and hinnies", shifting every
code below it by one row and attaching descriptions to their neighbours. If these files are
ever re-parsed, check the spot-checks the parser prints before trusting the output.
