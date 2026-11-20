"""Command line:

    python -m agents run       one run of every agent (what the Railway schedule calls)
    python -m agents check     checks the settings and that the store and AI can be reached
"""

import logging
import sys

from . import config, listing_agent
from .ai import AiClient, AiError
from .http import HttpError
from .store import StoreClient


def main(argv: list[str]) -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
    log = logging.getLogger("agents")
    cmd = argv[1] if len(argv) > 1 else "run"
    s = config.load()

    missing = s.missing()
    if missing:
        log.error("Not set up yet. Missing settings: %s", ", ".join(missing))
        return 2

    store = StoreClient(s.store_api_url, s.agents_token)
    ai = AiClient(s.ai_base_url, s.ai_api_key, s.ai_model, s.ai_timeout)

    if cmd == "check":
        ok = True
        try:
            cats = store.categories()
            log.info("Store OK: %d categories visible.", len(cats))
        except HttpError as e:
            ok = False
            log.error("Store NOT reachable with this token: %s", e)
        except Exception as e:
            ok = False
            log.error("Store NOT reachable: %s", e)
        try:
            ai.complete_json("Answer with a JSON object.", 'Reply with {"ok": true}.')
            log.info("AI OK: %s answered.", s.ai_model)
        except (AiError, HttpError, Exception) as e:
            ok = False
            log.error("AI NOT working: %s", e)
        return 0 if ok else 1

    if cmd == "run":
        try:
            summary = listing_agent.run(store, ai, s.batch_size, s.max_minutes)
        except HttpError as e:
            log.error("Could not talk to the store: %s", e)
            return 1
        except Exception as e:
            log.error("Could not reach the store: %s", e)
            return 1
        log.info("Listing Agent finished: %s", summary)
        return 0 if summary["errors"] == 0 else 1

    log.error("Unknown command %r. Use: run | check", cmd)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv))
