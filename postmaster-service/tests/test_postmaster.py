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
