"""Stock and Price Watcher. Reads what the supplier shows for each listed product and reports it.
The store decides what to do with each reading. Uses no AI."""

import logging
import time

from . import supplier

log = logging.getLogger("agents.watcher")


def run(store, read=supplier.read_stock, pause: float = 1.0) -> dict:
    targets = store.watch_targets()
    products = targets.get("products") or []
    readings, unreadable = [], 0
    for i, item in enumerate(products):
        if i and pause:
            time.sleep(pause)
        try:
            readings.append((item["productId"], read(item["sourceUrl"])))
        except supplier.SupplierError as e:
            unreadable += 1
            log.warning("Product %s not checked, nothing changed: %s", item.get("productId"), e)

    summary = {"checked": len(readings), "unreadable": unreadable, "changes": 0, "errors": 0, "live": bool(targets.get("live"))}
    out = sum(1 for _, r in readings if not r["available"])
    if len(readings) >= 3 and out * 2 > len(readings):
        # Most of a catalogue vanishing at once is far likelier to be a site fault than real.
        log.error("%d of %d products look out of stock at once. Nothing was reported.", out, len(readings))
        summary["errors"] = 1
        return summary

    for product_id, reading in readings:
        try:
            res = store.send_reading(product_id, reading) or {}
        except Exception as e:
            summary["errors"] += 1
            log.error("Product %s: the store did not accept the reading: %s", product_id, e)
            continue
        if res.get("action") not in (None, "NONE", "BASELINE"):
            summary["changes"] += 1
            log.info("Product %s: %s (%s). %s", product_id, res.get("action"),
                     "applied" if res.get("applied") else "preview only", res.get("reason"))
    return summary
