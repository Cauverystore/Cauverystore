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
- GSTN advisories on `gst.gov.in` ("News and Updates") are watched by the backend instead
  (`GstRuleWatchService`, the "GST rule changes" admin panel), through the page's own JSON at
  `/fomessage/newsupdates`. They are portal and procedure notices, not law, so they need no PDF
  archive here. gst.gov.in has no robots.txt (checked 2026-11-01). Its help modules
  (`/help/helpmodules/`) are a fixed index of manuals with no dated list; GSTN announces manual
  changes as advisories, so the advisory feed covers them.
- URLs marked `"verified": false` in the config have not been confirmed by a person yet. If one is wrong,
  the daily report lists it under "could not be checked" - fix the URL then.
- **Primary sources.** The two CBIC rate sources are marked `"primary": true`. If either cannot be read,
  the report opens with a warning, the "could not be checked" issue gets a comment every day, and the
  workflow run fails so GitHub emails the owner. Silence is never "no change".
- **What gets downloaded.** Only documents dated on or after `download_since` in `gst_sources.json`
  (the date of the newest notification already applied to `gst_rate_seed.json`). Older new links are
  recorded but not downloaded. Move the date forward whenever the seed catches up.
- **No repeats.** `manifest.json` is committed to main on every run and the review PR holds only the
  PDFs. Closing a review PR without merging it is fine: those documents will not be offered again.
  A manifest entry's `file` path then points at a PDF that only exists on the closed PR's branch.
- **Certificates.** `taxinformation.cbic.gov.in` sends its own certificate without the Sectigo
  intermediate that links it to a trusted root. Browsers and Windows fill the gap; Python on Linux
  (GitHub's runners) does not, so until 2026-11-01 every run failed with "could not read robots.txt"
  and CBIC was never checked. `certs/` holds that intermediate
  (`Sectigo Public Server Authentication CA OV R36`, valid to 2036, downloaded from
  `http://crt.sectigo.com/SectigoPublicServerAuthenticationCAOVR36.crt`); the watcher adds it to the
  system roots. Verification stays on. If CBIC changes certificate authority, the report will say
  "TLS certificate could not be verified": run
  `openssl s_client -connect taxinformation.cbic.gov.in:443 -showcerts`, take the issuer's URL from
  the certificate's "CA Issuers" field, convert it to PEM and add it to `certs/`.
- This reduces the chance of missing a change. It does not prove completeness. Have a CA review.
