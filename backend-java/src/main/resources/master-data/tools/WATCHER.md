# GST source watcher

Finds new GST notifications, circulars and corrigenda on open official sites and downloads the
PDFs for review. Runs daily from `.github/workflows/gst-source-watch.yml`.

- Sources and rules: `gst_sources.json`. Add a source only if its robots.txt allows automated reading.
- Record of what has been seen: `../cbic-source/manifest.json`. New downloads: `../cbic-source/incoming/`.
- Run by hand: `python -I watch_gst_sources.py --delay 3`
- Never edits `gst_rate_seed.json`. A new file means: read it, diff it against the seed, apply through
  `parse_cbic_notifications.py` and normal review.
- CBIC moved GST notifications from `cbic-gst.gov.in` (now 404) to `taxinformation.cbic.gov.in`.
  That portal builds its pages in JavaScript, so its sources use `"type": "cbic_api"`: the watcher
  reads the portal's own JSON (`fetchNotificationByCategory/1000001/<category>` for the list,
  `download/<id>/ENG` for the PDF, which comes base64-encoded). Its robots.txt (checked 2026-11-01)
  blocks only account, admin and api-docs paths. The workflow still opens a reminder issue on the
  1st of each month for a person to look at circulars and corrigenda, which the watcher does not read.
- `gstcouncil.gov.in/en/cgst-rate-notification` and `/en/cgst-circulars` are read as backup
  sources only. On 2026-11-01 they were far behind CBIC: the rate list stopped at 08/2025 (Jan 2025),
  missing 09/2025 and everything after it, and the circulars stopped at 250/07/2025-GST. "Nothing new
  on GST Council" therefore says nothing about whether rates have changed. CBIC is the source.
  (The old `/igst-rate-notification` page is 404 and was dropped.)
- URLs marked `"verified": false` in the config have not been confirmed by a person yet. If one is wrong,
  the daily report lists it under "could not be checked" - fix the URL then.
- This reduces the chance of missing a change. It does not prove completeness. Have a CA review.
