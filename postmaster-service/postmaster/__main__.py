"""One Postmaster run: take new mail, think, hand each suggestion to the store, then exit."""

import logging
import os
import sys

from .brain import Brain, BrainError
from .net import HttpError, request_json

log = logging.getLogger("postmaster")


class Store:
    def __init__(self, base_url: str, token: str):
        self.base = base_url.rstrip("/") + "/api/agent/postmaster"
        self.headers = {"X-Postmaster-Token": token}

    def mail(self, limit: int) -> dict:
        return request_json("GET", f"{self.base}/mail?limit={limit}", headers=self.headers) or {}

    def decide(self, mail_id, decision: dict) -> dict:
        return request_json("POST", f"{self.base}/mail/{mail_id}/decision", decision, self.headers) or {}


def run(store, brain, limit: int = 10) -> dict:
    got = store.mail(limit)
    summary = {"read": 0, "failed": 0, "enabled": bool(got.get("enabled")), "live": bool(got.get("live"))}
    for mail in got.get("mail") or []:
        summary["read"] += 1
        try:
            decision = brain.think(mail)
        except BrainError as e:
            log.error("Email %s: %s", mail.get("id"), e)
            summary["failed"] += 1
            decision = {"action": "person", "reason": "The AI could not answer this one."}
        try:
            result = store.decide(mail["id"], decision)
            log.info("Email %s: %s", mail["id"], result.get("status"))
        except (HttpError, OSError) as e:
            # The store hands unfinished mail to a person after 20 minutes.
            log.error("Email %s: could not report back: %s", mail.get("id"), e)
            summary["failed"] += 1
    return summary


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    need = ["STORE_API_URL", "POSTMASTER_TOKEN", "AI_BASE_URL", "AI_API_KEY", "AI_MODEL"]
    # Values pasted into a dashboard often carry a stray space or line break.
    env = {n: (os.environ.get(n) or "").strip() for n in need}
    missing = [n for n in need if not env[n]]
    if missing:
        # Stops before taking any mail, so nothing gets stuck.
        log.error("Missing settings: %s", ", ".join(missing))
        return 2
    try:
        limit = max(1, min(int(os.environ.get("POSTMASTER_BATCH", "10")), 25))
    except ValueError:
        limit = 10
    for n in ("POSTMASTER_TOKEN", "AI_API_KEY"):
        if any(c.isspace() or ord(c) < 32 for c in env[n]):
            log.error("%s has a space or line break inside it; paste it again", n)   # never the value
            return 2
    store = Store(env["STORE_API_URL"], env["POSTMASTER_TOKEN"])
    try:
        brain = Brain(env["AI_BASE_URL"], env["AI_API_KEY"], env["AI_MODEL"])
    except BrainError as e:
        log.error("AI settings: %s", e)
        return 2
    try:
        log.info("Postmaster finished: %s", run(store, brain, limit))
    except (HttpError, OSError) as e:
        log.error("Could not reach the store: %s", e)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
