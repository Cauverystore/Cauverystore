"""The store's agent door (/api/agent/**). Agents can take jobs, read categories, hand back drafts
and report supplier readings; they cannot create or edit products."""

from .http import request_json


class StoreClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip("/")
        self.headers = {"X-Agent-Token": token}

    def take_jobs(self, agent: str, limit: int) -> list[dict]:
        res = request_json("GET", f"{self.base_url}/api/agent/{agent}/jobs?limit={limit}", headers=self.headers)
        return (res or {}).get("jobs", [])

    def pending(self, agent: str) -> int:
        """How many jobs wait for this agent, without taking any."""
        res = request_json("GET", f"{self.base_url}/api/agent/{agent}/pending", headers=self.headers)
        return int((res or {}).get("pending", 0))

    def categories(self) -> list[dict]:
        return request_json("GET", f"{self.base_url}/api/agent/categories", headers=self.headers) or []

    def send_draft(self, job_id: int, draft: dict) -> dict:
        return request_json("POST", f"{self.base_url}/api/agent/jobs/{job_id}/result", draft, headers=self.headers)

    def watch_targets(self) -> dict:
        return request_json("GET", f"{self.base_url}/api/agent/watch/products", headers=self.headers) or {}

    def send_reading(self, product_id: int, reading: dict) -> dict:
        return request_json("POST", f"{self.base_url}/api/agent/watch/products/{product_id}", reading, headers=self.headers)

    def send_failure(self, job_id: int, reason: str) -> dict:
        return request_json("POST", f"{self.base_url}/api/agent/jobs/{job_id}/result", {"failed": reason[:1000]}, headers=self.headers)
