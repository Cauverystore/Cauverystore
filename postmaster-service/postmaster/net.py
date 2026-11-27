"""JSON over HTTP on the standard library, so the app needs no installs."""

import json
import urllib.error
import urllib.request


class HttpError(Exception):
    def __init__(self, status: int, body: str):
        super().__init__(f"HTTP {status}")
        self.status = status
        self.body = body


class _NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):  # secrets must never follow a redirect elsewhere
        return None


_opener = urllib.request.build_opener(_NoRedirect)


def request_json(method: str, url: str, body=None, headers=None, timeout: int = 60):
    data = None
    h = {"Accept": "application/json", "User-Agent": "Postmaster/1.0"}
    if headers:
        h.update(headers)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        raw = _send(req, timeout)
    except ValueError:
        # http.client puts a bad header's value (a secret) in its message: never pass it on.
        raise OSError("a request header was not valid (check settings for stray spaces)") from None
    return json.loads(raw) if raw.strip() else None


def _send(req, timeout):
    try:
        with _opener.open(req, timeout=timeout) as res:
            raw = res.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raise HttpError(e.code, e.read().decode("utf-8", errors="replace")) from None
    return raw
