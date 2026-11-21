import unittest

from agents import supplier, watcher


class FakeStore:
    def __init__(self, n):
        self.items = [{"productId": i, "sourceUrl": f"https://deodap.in/products/p{i}"} for i in range(1, n + 1)]
        self.sent = []

    def watch_targets(self):
        return {"products": self.items, "live": True}

    def send_reading(self, product_id, reading):
        self.sent.append((product_id, reading))
        return {"action": "HIDE" if not reading["available"] else "NONE", "applied": True, "reason": "x"}


IN = {"available": True, "cost": 100.0, "mrp": 299.0}
OUT = {"available": False, "cost": None, "mrp": None}


class WatcherTest(unittest.TestCase):
    def test_reports_each_reading(self):
        s = FakeStore(3)
        r = watcher.run(s, read=lambda u: OUT if u.endswith("p2") else IN, pause=0)
        self.assertEqual(len(s.sent), 3)
        self.assertEqual(r["changes"], 1)

    def test_mass_outage_changes_nothing(self):
        s = FakeStore(4)
        r = watcher.run(s, read=lambda u: OUT if not u.endswith("p1") else IN, pause=0)
        self.assertEqual(s.sent, [])
        self.assertEqual(r["errors"], 1)

    def test_unreadable_product_is_left_alone(self):
        s = FakeStore(2)
        def read(u):
            if u.endswith("p1"):
                raise supplier.SupplierError("site down")
            return IN
        r = watcher.run(s, read=read, pause=0)
        self.assertEqual([p for p, _ in s.sent], [2])
        self.assertEqual(r["unreadable"], 1)


if __name__ == "__main__":
    unittest.main()
