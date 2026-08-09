# -*- coding: utf-8 -*-
"""
Downloads the GSTN Master Codes page on this machine (GSTN only answers
reliably from India) and uploads it to the importer API, which then writes
the tables into the database.

Usage:
    python fetch_upload_master.py [API_URL]

API_URL defaults to the production importer API. The API token is read from
GST_IMPORTER_API_TOKEN (or pass --token). Returns 0 on success.
"""

import os
import sys
import argparse
import json
import urllib.request

API_DEFAULT = "https://gst-importer-api-production.up.railway.app"
PAGE_URL = os.environ.get("GST_MASTER_CODES_URL", "https://einvoice1.gst.gov.in/Others/MasterCodes")


def main():
    parser = argparse.ArgumentParser(description="Download the GSTN Master Codes page and upload it to the importer API")
    parser.add_argument("api_url", nargs="?", default=API_DEFAULT)
    parser.add_argument("--token", default=os.environ.get("GST_IMPORTER_API_TOKEN", ""))
    args = parser.parse_args()

    print("downloading %s ..." % PAGE_URL)
    req = urllib.request.Request(PAGE_URL, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=300) as resp:
        html = resp.read().decode("utf-8", errors="replace")
    print("downloaded %d chars" % len(html))

    payload = html.encode("utf-8")
    req = urllib.request.Request(
        args.api_url.rstrip("/") + "/upload-master-codes",
        data=payload,
        headers={"Content-Type": "text/html; charset=utf-8",
                 "Authorization": "Bearer " + args.token},
        method="POST",
    )
    print("uploading to %s ..." % args.api_url)
    with urllib.request.urlopen(req, timeout=1200) as resp:
        body = resp.read().decode("utf-8", errors="replace")
    print(body)
    try:
        return 0 if int(json.loads(body)["exit_code"]) == 0 else 1
    except Exception:
        return 1


if __name__ == "__main__":
    sys.exit(main())