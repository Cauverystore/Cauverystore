import unittest

from postmaster.__main__ import run
from postmaster.brain import BrainError, parse


class FakeStore:
    def __init__(self, mail):
        self.items = mail
        self.decisions = {}

    def mail(self, limit):
        return {"enabled": True, "live": False, "mail": self.items}

    def decide(self, mail_id, decision):
        self.decisions[mail_id] = decision
        return {"status": "PREVIEW"}


class GoodBrain:
    def think(self, mail):
        return {"action": "reply", "intent": "POLICY", "confidence": 0.9, "reply": "Here is the answer.", "reason": "faq"}


class BrokenBrain:
    def think(self, mail):
        raise BrainError("down")


class PostmasterTest(unittest.TestCase):
    def test_a_suggestion_goes_to_the_store(self):
        store = FakeStore([{"id": 1}])
        summary = run(store, GoodBrain())
        self.assertEqual(summary["read"], 1)
        self.assertEqual(store.decisions[1]["action"], "reply")

    def test_when_the_ai_fails_the_mail_goes_to_a_person(self):
        store = FakeStore([{"id": 2}])
        summary = run(store, BrokenBrain())
        self.assertEqual(summary["failed"], 1)
        self.assertEqual(store.decisions[2]["action"], "person")

    def test_answers_are_read_even_with_extra_text(self):
        value = parse('<think>hmm</think> Sure: {"action": "reply", "intent": "PRODUCT", "confidence": 0.9, "reply": "Yes"}')
        self.assertEqual(value["intent"], "PRODUCT")

    def test_a_missing_action_means_a_person(self):
        self.assertEqual(parse('{"reply": "x"}')["action"], "person")

    def test_a_broken_answer_is_refused(self):
        with self.assertRaises(BrainError):
            parse("no json here")


if __name__ == "__main__":
    unittest.main()


class ClaudeTest(unittest.TestCase):
    def setUp(self):
        import postmaster.brain as b
        self.b = b
        self.sent = []
        self.answer = {"stop_reason": "end_turn", "content": [
            {"type": "thinking", "thinking": "..."},
            {"type": "text", "text": '{"action": "reply", "intent": "DELIVERY", "confidence": 0.9, "reply": "Soon."}'}]}
        self.real = b.request_json
        b.request_json = lambda method, url, body, headers, timeout=60: (self.sent.append((url, body, headers)), self.answer)[1]

    def tearDown(self):
        self.b.request_json = self.real

    def test_claude_is_asked_in_its_own_way(self):
        brain = self.b.Brain("https://api.anthropic.com", "k", "claude-haiku-5-5")
        out = brain.think({"subject": "Where is my order", "message": "?"})
        self.assertEqual("reply", out["action"])
        url, body, headers = self.sent[0]
        self.assertEqual("https://api.anthropic.com/v1/messages", url)
        self.assertEqual("k", headers["x-api-key"])
        self.assertNotIn("Authorization", headers)
        self.assertIn("system", body)
        self.assertNotIn("response_format", body)

    def test_an_unfinished_claude_answer_goes_to_a_person(self):
        self.answer = {"stop_reason": "max_tokens", "content": [{"type": "text", "text": '{"action": "reply"'}]}
        brain = self.b.Brain("https://api.anthropic.com/", "k", "claude-haiku-5-5")
        with self.assertRaises(self.b.BrainError):
            brain.think({"subject": "s", "message": "m"})

    def test_a_plain_http_address_is_refused(self):
        with self.assertRaises(self.b.BrainError):
            self.b.Brain("http://example.com/v1", "k", "m")


class SecretSafetyTest(unittest.TestCase):
    ENV = {"STORE_API_URL": "https://example.com", "POSTMASTER_TOKEN": "t" * 64,
           "AI_BASE_URL": "https://api.anthropic.com", "AI_MODEL": "claude-haiku-5-5"}

    def test_a_key_with_a_line_break_is_never_logged(self):
        import os
        from unittest import mock
        from postmaster import __main__ as m
        env = dict(self.ENV, AI_API_KEY="sk-ant-SECRETVALUE\nmore")
        with mock.patch.dict(os.environ, env), self.assertLogs("postmaster", level="ERROR") as logs:
            self.assertEqual(2, m.main())
        self.assertNotIn("SECRETVALUE", " ".join(logs.output))

    def test_a_trailing_space_is_trimmed(self):
        import os
        from unittest import mock
        from postmaster import __main__ as m
        env = dict(self.ENV, AI_API_KEY="sk-ant-x \n", POSTMASTER_TOKEN="t" * 64 + "\n")
        with mock.patch.dict(os.environ, env), mock.patch.object(m, "run", return_value={}) as run:
            self.assertEqual(0, m.main())
        self.assertEqual("sk-ant-x", run.call_args[0][1].api_key)

    def test_a_bad_header_error_hides_its_value(self):
        from postmaster.net import request_json
        with self.assertRaises(OSError) as caught:
            request_json("GET", "https://example.invalid/", headers={"x-api-key": "sk-ant-SECRET\nX"})
        self.assertNotIn("SECRET", str(caught.exception))
