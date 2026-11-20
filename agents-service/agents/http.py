"""Tiny JSON-over-HTTP helper on the standard library, so the service needs no installs."""

import json
import urllib.error
import urllib.request


class HttpError(Exception):
    def __init__(self, status: int, body: str):
        super().__init__(f"HTTP {status}: {body[:300]}")
        self.status = status
        self.body = body


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # never follow redirects to other places
        return None


_opener = urllib.request.build_opener(_NoRedirect)


def request_json(method: str, url: str, body=None, headers=None, timeout: int = 60):
    data = None
    h = {"Accept": "application/json", "User-Agent": "Mozilla/5.0 (compatible; StoreAgents/1.0)"}
    if headers:
        h.update(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        with _opener.open(req, timeout=timeout) as res:
            raw = res.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raise HttpError(e.code, e.read().decode("utf-8", errors="replace")) from None
    return json.loads(raw) if raw.strip() else None
