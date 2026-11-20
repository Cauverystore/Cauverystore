import http.server
import threading
import time
import unittest

from agents.ai import AiClient, AiError


class _Slow(http.server.BaseHTTPRequestHandler):
    def do_POST(self):
        time.sleep(3)
        self.send_response(200)
        self.end_headers()

    def log_message(self, *a):
        pass


class SlowBrainTest(unittest.TestCase):
    def test_slow_answer_becomes_ai_error(self):
        srv = http.server.HTTPServer(("127.0.0.1", 0), _Slow)
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try:
            ai = AiClient(f"http://127.0.0.1:{srv.server_port}/v1", "", "m", timeout=1)
            with self.assertRaises(AiError) as cm:
                ai.complete_json("s", "u")
            self.assertIn("took longer", str(cm.exception))
        finally:
            srv.shutdown()

    def test_no_brain_becomes_ai_error(self):
        ai = AiClient("http://127.0.0.1:9/v1", "", "m", timeout=2)
        with self.assertRaises(AiError):
            ai.complete_json("s", "u")


if __name__ == "__main__":
    unittest.main()
