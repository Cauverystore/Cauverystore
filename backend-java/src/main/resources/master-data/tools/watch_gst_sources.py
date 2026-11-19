#!/usr/bin/env python3
"""
Watches the OPEN official GST sources for new notifications / circulars / corrigenda and
downloads the PDFs, so a human can review them against gst_rate_seed.json.

What it does
  * For every source in gst_sources.json: checks robots.txt, fetches the listing page(s),
    finds PDF links, and compares them with cbic-source/manifest.json.
  * Sources with "type": "cbic_api" read CBIC's tax information portal
    (taxinformation.cbic.gov.in), which replaced cbic-gst.gov.in. That portal builds its pages
    in JavaScript, so its HTML has no PDF links to find. Instead this reads the same JSON its
    own pages read: the notification list for one category, and each notification's PDF,
    which the portal returns base64-encoded inside JSON.
  * New PDFs dated on or after `download_since` are downloaded into
    cbic-source/incoming/<source id>/ with a SHA-256 and a manifest entry. Undated ones fall back
    to `download_if_matches`. Older new links are only recorded, so documents already reflected
    in gst_rate_seed.json are not offered for review again.
  * Sources marked "primary": true are the ones rate changes appear on first. If one of them
    could not be read, report.json says so ("primary_unchecked") and the workflow fails loudly.
  * Writes report.md / report.json: new files, sources it could not check (health), and
    sources that are manual-only.

What it deliberately does NOT do
  * It never edits gst_rate_seed.json. A new file is a prompt for review, not a rate change.
  * It never fetches a site whose robots.txt disallows it, never follows PDF links to hosts
    outside the source's allowed_hosts, and never logs in to anything.
  * It never treats "no new files" as "nothing changed": a source that is unreachable,
    disallowed, or suddenly returns zero PDF links is reported as a health problem.

Python standard library only.
"""
import argparse
import base64
import datetime as dt
import hashlib
import json
import re
import ssl
import sys
import time
from html.parser import HTMLParser
from pathlib import Path
from urllib import error, request, robotparser
from urllib.parse import parse_qsl, quote, urlencode, urljoin, urlparse, urlunparse

UA = "CauveryStoreGSTWatch/1.0 (+https://cauverystore.in; compliance monitor)"
MAX_BYTES = 25 * 1024 * 1024
TIMEOUT = 30
CERT_DIR = Path(__file__).resolve().parent / "certs"
_CTX = None


def _ssl_context():
    """
    The system's trusted roots plus the intermediate certificates in certs/. Some official sites
    (taxinformation.cbic.gov.in, Nov 2026) send their own certificate without the intermediate that
    links it to a trusted root. Browsers and Windows fetch the missing piece; Python on Linux does
    not, so the site looked unreachable from GitHub. Verification stays fully on.
    """
    ctx = ssl.create_default_context()
    for pem in sorted(CERT_DIR.glob("*.pem")):
        ctx.load_verify_locations(cafile=str(pem))
    return ctx


def _why(e):
    """A readable reason for a failed request."""
    reason = getattr(e, "reason", None)
    if isinstance(e, ssl.SSLCertVerificationError) or isinstance(reason, ssl.SSLCertVerificationError):
        return ("TLS certificate could not be verified (%s). The site may have changed its certificate "
                "chain: see 'Certificates' in WATCHER.md" % (getattr(reason, "verify_message", None) or e))
    if isinstance(e, error.HTTPError):
        return "HTTP %s" % e.code
    return str(reason or e)


class _Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.links, self._href, self._text = [], None, []

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            self._href, self._text = dict(attrs).get("href"), []

    def handle_data(self, data):
        if self._href is not None:
            self._text.append(data)

    def handle_endtag(self, tag):
        if tag == "a" and self._href is not None:
            self.links.append((self._href, " ".join("".join(self._text).split())))
            self._href = None


def _get(url, limit=MAX_BYTES):
    global _CTX
    if _CTX is None:
        _CTX = _ssl_context()
    req = request.Request(url, headers={"User-Agent": UA, "Accept": "*/*"})
    ctx = _CTX if urlparse(url).scheme == "https" else None
    with request.urlopen(req, timeout=TIMEOUT, context=ctx) as r:
        data = r.read(limit + 1)
    if len(data) > limit:
        raise ValueError("response larger than %d bytes" % limit)
    return data


class Robots:
    """robots.txt per host. Anything other than a clean answer means 'do not fetch'."""

    def __init__(self):
        self._cache = {}
        self._failed = {}  # host -> why robots.txt could not be read

    def allowed(self, url):
        p = urlparse(url)
        host = "%s://%s" % (p.scheme, p.netloc)
        if host not in self._cache:
            rp = robotparser.RobotFileParser()
            try:
                rp.parse(_get(host + "/robots.txt", 1024 * 1024).decode("utf-8", "replace").splitlines())
                self._cache[host] = rp
            except error.HTTPError as e:
                # 404 = no robots file = no restrictions. 401/403/5xx = do not fetch.
                self._cache[host] = True if e.code == 404 else False
                if e.code != 404:
                    self._failed[host] = _why(e)
            except Exception as e:
                self._cache[host] = False
                self._failed[host] = _why(e)
        rp = self._cache[host]
        if rp is True or rp is False:
            return rp
        return rp.can_fetch(UA, url)

    def refusal(self, url):
        """Why allowed() said no: robots.txt unreadable (with the reason) or a real disallow."""
        p = urlparse(url)
        why = self._failed.get("%s://%s" % (p.scheme, p.netloc))
        return "could not read robots.txt: %s" % why if why else "robots.txt disallows it"


def _page_urls(src):
    base, pages = src["url"], int(src.get("pages", 1))
    if pages <= 1:
        return [base]
    p = urlparse(base)
    out = []
    for n in range(pages):
        q = dict(parse_qsl(p.query))
        q["page"] = str(n)
        out.append(urlunparse(p._replace(query=urlencode(q))))
    return out


def _html_listing(src, page_data):
    """PDF links on ordinary HTML listing pages: {url: (title, text to match)}."""
    found = {}
    hosts = set(src["allowed_hosts"])
    for page, html in page_data:
        parser = _Links()
        parser.feed(html.decode("utf-8", "replace"))
        for href, text in parser.links:
            if not href:
                continue
            url = urljoin(page, href).split("#")[0]
            u = urlparse(url)
            if u.path.lower().endswith(".pdf") and u.hostname in hosts:
                title = text or Path(u.path).name
                # Drupal sites file uploads under /files/YYYY-MM/: the best date the page offers.
                m = re.search(r"/files/(20\d\d)-(\d\d)/", u.path)
                date = "%s-%s-01" % m.groups() if m else ""
                found.setdefault(url, (title, url + " " + title, date))
    return found


def _api_listing_url(src):
    return "%s/fetchNotificationByCategory/%s/%s" % (
        src["api_base"].rstrip("/"), src["tax_id"], quote(src["category"], safe=""))


def _api_listing(src, data):
    """
    One CBIC portal category list: {download url: (title, text to match)}. The download URL is
    the manifest key, so it is built from the portal's numeric document id, which does not change.
    """
    items = json.loads(data.decode("utf-8"))
    if not isinstance(items, list):
        raise ValueError("expected a JSON list, got %s" % type(items).__name__)
    found = {}
    for n in items:
        if n.get("id") is None or str(n.get("isActive") or "Y").upper() == "N":
            continue
        number = " ".join(str(n.get("notificationNo") or "").split())
        date = str(n.get("notificationDt") or "")[:10]
        name = " ".join(str(n.get("notificationName") or "").split())
        url = "%s/download/%s/ENG" % (src["api_base"].rstrip("/"), n["id"])
        title = "%s (%s) - %s" % (number, date, name) if date else "%s - %s" % (number, name)
        # Match on number and date only: names often quote the 2017 notification they amend.
        found[url] = (title, number + " " + date, date)
    return found


def _wanted(match_text, date, since, pattern):
    """Download only documents dated on or after `since`; fall back to the pattern when undated."""
    if since and re.match(r"\d{4}-\d\d-\d\d$", date or ""):
        return date >= since
    return bool(pattern.search(match_text))


def _api_document(data):
    """The portal answers {"fileName": ..., "data": <base64 PDF>}."""
    root = json.loads(data.decode("utf-8"))
    return base64.b64decode(root.get("data") or ""), root.get("fileName") or ""


def run(cfg, manifest, out_dir, allow_local=False, delay=3.0):
    rep = {"checked": [], "new": [], "recorded_only": [], "health": [], "manual": [],
           "primary_unchecked": []}
    robots = Robots()
    today = dt.date.today().isoformat()
    pattern = re.compile(cfg.get("download_if_matches", r"20(25|26|27|28)"))
    since = cfg.get("download_since", "")
    budget = int(cfg.get("max_downloads_per_run", 40))

    def fetchable(url):
        scheme = urlparse(url).scheme
        host = urlparse(url).hostname
        if scheme != "https" and not (allow_local and host in ("127.0.0.1", "localhost")):
            return False
        return robots.allowed(url)

    for src in cfg["sources"]:
        if src.get("manual_only"):
            rep["manual"].append({"id": src["id"], "label": src["label"], "url": src["url"],
                                  "reason": src.get("reason", "")})
            continue
        is_api = src.get("type") == "cbic_api"
        pages = [_api_listing_url(src)] if is_api else _page_urls(src)
        page_data = []
        for page in pages:
            if not fetchable(page):
                rep["health"].append("%s: not fetched (%s): %s" % (src["id"], robots.refusal(page), page))
                continue
            time.sleep(delay)
            try:
                page_data.append((page, _get(page)))
            except Exception as e:
                rep["health"].append("%s: could not read %s (%s)" % (src["id"], page, _why(e)))
        try:
            if is_api:
                found = _api_listing(src, page_data[0][1]) if page_data else {}
            else:
                found = _html_listing(src, page_data)
        except Exception as e:
            rep["health"].append("%s: could not understand the listing (%s) - the site may have "
                                 "changed; this source is NOT being checked" % (src["id"], e))
            if src.get("primary"):
                rep["primary_unchecked"].append(src["label"])
            continue
        if page_data:  # "checked" only when something was actually read
            rep["checked"].append({"id": src["id"], "label": src["label"], "pdf_links": len(found)})
        if not found:
            if page_data:
                rep["health"].append("%s: found 0 documents - the page layout may have changed or the "
                                     "site is down; this source is NOT being checked" % src["id"])
            if src.get("primary"):
                rep["primary_unchecked"].append(src["label"])
            continue
        for url, (title, match_text, date) in found.items():
            if url in manifest:
                continue
            entry = {"source": src["id"], "title": title, "first_seen": today}
            if not _wanted(match_text, date, since, pattern):
                entry["downloaded"] = False
                manifest[url] = entry
                rep["recorded_only"].append(url)
                continue
            if budget <= 0:
                continue  # stays unrecorded, picked up on the next run
            if not fetchable(url):
                rep["health"].append("%s: PDF not fetched (robots.txt): %s" % (src["id"], url))
                continue
            time.sleep(delay)
            try:
                data, served_name = _api_document(_get(url)) if is_api else (_get(url), "")
            except Exception as e:
                rep["health"].append("%s: download failed %s (%s)" % (src["id"], url, _why(e)))
                continue
            if not data.startswith(b"%PDF"):
                rep["health"].append("%s: not a PDF (skipped): %s" % (src["id"], url))
                continue
            digest = hashlib.sha256(data).hexdigest()
            raw_name = served_name or Path(urlparse(url).path).name
            if is_api and not raw_name.lower().endswith(".pdf"):
                raw_name = re.sub(r"[/ ]+", "-", title.split(" (")[0]) + ".pdf"
            name = re.sub(r"[^A-Za-z0-9._-]", "_", raw_name) or "file.pdf"
            dest = out_dir / src["id"] / name
            if dest.exists():
                dest = dest.with_name(digest[:8] + "_" + name)
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(data)
            entry.update({"downloaded": True, "sha256": digest, "file": str(dest.as_posix()),
                          "bytes": len(data)})
            manifest[url] = entry
            budget -= 1
            rep["new"].append({"source": src["id"], "title": title, "url": url,
                               "file": entry["file"], "sha256": digest})
    return rep


def to_markdown(rep):
    L = ["# GST source watch - %s" % dt.date.today().isoformat(), ""]
    if rep["primary_unchecked"]:
        L += ["> [!CAUTION]",
              "> **The main source was NOT checked today:** %s. Rate changes are published there first, "
              "so a new GST rate could be missed. Check https://taxinformation.cbic.gov.in by hand "
              "(GST > Notifications > Central Tax (Rate)) until this is fixed." % "; ".join(rep["primary_unchecked"]),
              ""]
    L.append("## New files (%d)" % len(rep["new"]))
    for n in rep["new"]:
        L.append("- **%s** - [%s](%s)  \n  saved as `%s`  \n  sha256 `%s`"
                 % (n["source"], n["title"], n["url"], n["file"], n["sha256"][:16]))
    if not rep["new"]:
        L.append("None.")
    L += ["", "**Next step for each file:** read it, decide whether it changes a rate, exemption, "
          "classification or a return/TCS rule, then diff against `gst_rate_seed.json` and apply "
          "through the normal parser + review. Do not edit the seed from this report alone.", ""]
    L.append("## Sources that could NOT be checked (%d)" % len(rep["health"]))
    L += ["- " + h for h in rep["health"]] or ["None."]
    if rep["manual"]:
        L += ["", "## Manual-only sources - check these yourself this month"]
        for m in rep["manual"]:
            L.append("- [%s](%s) - %s" % (m["label"], m["url"], m["reason"]))
    L += ["", "## Checked"]
    for c in rep["checked"]:
        L.append("- %s: %d document(s) seen" % (c["label"], c["pdf_links"]))
    L.append("- Older links recorded without download this run: %d" % len(rep["recorded_only"]))
    L += ["", "_This tool finds documents on open official sites. It cannot prove nothing else was "
          "published elsewhere. A CA should review the compliance register._"]
    return "\n".join(L) + "\n"


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    here = Path(__file__).resolve().parent
    ap.add_argument("--config", default=str(here / "gst_sources.json"))
    ap.add_argument("--source-dir", default=str(here.parent / "cbic-source"))
    ap.add_argument("--report-dir", default=".")
    ap.add_argument("--delay", type=float, default=3.0, help="seconds between requests")
    ap.add_argument("--allow-local", action="store_true", help="tests only: permit http://localhost")
    a = ap.parse_args(argv)

    cfg = json.loads(Path(a.config).read_text(encoding="utf-8"))
    src_dir = Path(a.source_dir)
    mpath = src_dir / "manifest.json"
    manifest = json.loads(mpath.read_text(encoding="utf-8")) if mpath.exists() else {}

    rep = run(cfg, manifest, src_dir / "incoming", a.allow_local, a.delay)

    src_dir.mkdir(parents=True, exist_ok=True)
    mpath.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    rdir = Path(a.report_dir)
    rdir.mkdir(parents=True, exist_ok=True)
    (rdir / "report.md").write_text(to_markdown(rep), encoding="utf-8")
    (rdir / "report.json").write_text(json.dumps(
        {"new": len(rep["new"]), "health": len(rep["health"]),
         "primary_unchecked": len(rep["primary_unchecked"])}) + "\n", encoding="utf-8")
    print("new=%d health_problems=%d primary_unchecked=%d"
          % (len(rep["new"]), len(rep["health"]), len(rep["primary_unchecked"])))
    return 0


if __name__ == "__main__":
    sys.exit(main())
