# -*- coding: utf-8 -*-
"""
Pushes the Excel master workbook to the importer API, which loads every
sheet (hsn, sac, rates and the five code lists) into the database.

Usage:
    python push_master_sheet.py [workbook.xlsx] [API_URL]

The workbook defaults to GST_Master.xlsx in this folder; API_URL defaults
to the production importer API. The token is read from GST_IMPORTER_API_TOKEN
(or pass --token). Returns 0 on success.
"""

import os
import sys
import json
import argparse
import urllib.request

API_DEFAULT = "https://gst-importer-api-production.up.railway.app"


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    parser = argparse.ArgumentParser(description="Upload the GST master workbook to the importer API")
    parser.add_argument("workbook", nargs="?", default=os.path.join(here, "GST_Master.xlsx"))
    parser.add_argument("api_url", nargs="?", default=API_DEFAULT)
    parser.add_argument("--token", default=os.environ.get("GST_IMPORTER_API_TOKEN", ""))
    args = parser.parse_args()

    with open(args.workbook, "rb") as fh:
        payload = fh.read()
    print("uploading %s (%d bytes) to %s ..." % (args.workbook, len(payload), args.api_url))

    req = urllib.request.Request(
        args.api_url.rstrip("/") + "/upload-master-sheet",
        data=payload,
        headers={"Content-Type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "Authorization": "Bearer " + args.token},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=1200) as resp:
            body = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        print(body)
        return 1
    print(body)
    try:
        return 0 if json.loads(body).get("exit_code") == 0 else 1
    except Exception:
        return 1


if __name__ == "__main__":
    sys.exit(main())