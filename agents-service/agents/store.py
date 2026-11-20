"""The store's agent door (/api/agent/**). Agents can only take jobs, read categories and hand
back drafts; they cannot create or change products."""

from .http import request_json


class StoreClient:
    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip("/")
        self.headers = {"X-Agent-Token": token}

    def take_jobs(self, agent: str, limit: int) -> list[dict]:
        res = request_json("GET", f"{self.base_url}/api/agent/{agent}/jobs?limit={limit}", headers=self.headers)
        return (res or {}).get("jobs", [])

    def categories(self) -> list[dict]:
        return request_json("GET", f"{self.base_url}/api/agent/categories", headers=self.headers) or []

    def send_draft(self, job_id: int, draft: dict) -> dict:
        return request_json("POST", f"{self.base_url}/api/agent/jobs/{job_id}/result", draft, headers=self.headers)

    def send_failure(self, job_id: int, reason: str) -> dict:
        return request_json("POST", f"{self.base_url}/api/agent/jobs/{job_id}/result", {"failed": reason[:1000]}, headers=self.headers)
