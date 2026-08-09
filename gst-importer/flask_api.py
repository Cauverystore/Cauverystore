# -*- coding: utf-8 -*-
"""
Flask API that triggers the GST master importer.

Endpoints:
    GET  /                        service info + last run
    POST /update-gst-master       run the importer (subprocess), returns result
    GET  /update-gst-master/status  last run result

The importer runs as a subprocess so the API host and the executable are the
same process boundary that the cron wrapper uses. Set GST_IMPORTER_EXE to point
at the packaged .exe; otherwise the same script is run with the current Python.

Security: if GST_IMPORTER_API_TOKEN is set, requests must carry
`Authorization: Bearer <token>` (or X-API-Key). Nothing blocks the database
writes without it, so set it before exposing this on a network.

Run:  python flask_api.py            (defaults to 127.0.0.1:5001)
"""

import json
import os
import subprocess
import sys
import threading
import time

from flask import Flask, jsonify, request
from flask_cors import CORS

import config as cfg

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
app = Flask(__name__)
CORS(app, origins=cfg.cors_origins())

_lock = threading.Lock()
_last_run = {"status": "never", "started": None, "finished": None}


def _importer_command():
    exe = cfg.importer_exe()
    if exe and os.path.exists(exe):
        return [exe, "--trigger", "api"]
    return [sys.executable, os.path.join(BASE_DIR, "gst_master_importer.py"), "--trigger", "api"]


def _write_last_run(payload):
    payload["ts"] = time.strftime("%Y-%m-%d %H:%M:%S")
    _last_run.update(payload)
    try:
        path = os.path.join(BASE_DIR, "logs", "last_run.json")
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
    except Exception:  # noqa: BLE001 - status file is best-effort
        pass


def _append_log(text):
    try:
        with open(cfg.log_file(), "a", encoding="utf-8") as fh:
            fh.write(text + "\n")
    except Exception:  # noqa: BLE001 - logging must never break the request
        pass


def _authorized():
    token = cfg.api_token()
    if not token:
        return True
    header = request.headers.get("Authorization", "")
    if header == "Bearer " + token:
        return True
    return request.headers.get("X-API-Key") == token


@app.before_request
def _guard():
    if request.method == "POST" and not _authorized():
        return jsonify({"error": "Unauthorized - provide the GST importer API token"}), 401
    return None


@app.route("/", methods=["GET"])
def index():
    return jsonify({
        "service": "gst-master-importer-api",
        "status": "ok",
        "last_run": _last_run,
    })


@app.route("/update-gst-master", methods=["POST"])
def update_gst_master():
    if not _lock.acquire(blocking=False):
        return jsonify({"error": "An update is already running - try again shortly."}), 409

    started = time.time()
    started_at = time.strftime("%Y-%m-%d %H:%M:%S")
    _append_log("[%s] Update triggered via API" % started_at)
    try:
        cmd = _importer_command()
        try:
            proc = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=cfg.run_timeout(),
                cwd=BASE_DIR,
            )
        except subprocess.TimeoutExpired:
            _write_last_run({"status": "error", "started": started_at,
                             "finished": None, "error": "Importer timed out after %ss" % cfg.run_timeout()})
            _append_log("[%s] Update FAILED: timed out" % started_at)
            return jsonify({"status": "error",
                            "error": "Importer timed out after %ss" % cfg.run_timeout()}), 504

        elapsed = round(time.time() - started, 1)
        output = (proc.stdout or "") + "\n" + (proc.stderr or "")
        tail = output.strip().splitlines()[-25:]
        result = {"started": started_at, "finished": time.strftime("%Y-%m-%d %H:%M:%S"),
                  "duration_seconds": elapsed, "exit_code": proc.returncode,
                  "output_tail": tail}

        if proc.returncode == 0:
            summary = None
            for line in tail:
                if line.startswith("RESULT "):
                    summary = line[len("RESULT "):]
                    break
            result.update({"status": "success", "summary": summary})
            _write_last_run(result)
            _append_log("[%s] GST Master Codes updated successfully" % time.strftime("%Y-%m-%d %H:%M:%S"))
            return jsonify(result)
        result.update({"status": "error", "error": tail[-1] if tail else "Unknown failure"})
        _write_last_run(result)
        _append_log("[%s] Update FAILED (exit %s)" % (time.strftime("%Y-%m-%d %H:%M:%S"), proc.returncode))
        return jsonify(result), 500
    finally:
        _lock.release()


@app.route("/update-gst-master/status", methods=["GET"])
def status():
    return jsonify(_last_run)


if __name__ == "__main__":
    host = os.environ.get("GST_IMPORTER_HOST", "0.0.0.0")
    port = int(os.environ.get("GST_IMPORTER_PORT", os.environ.get("PORT", "5001")))
    app.run(host=host, port=port, threaded=True)
