"""Settings, all read from environment variables (set on Railway, or in a local .env file)."""

import os
from dataclasses import dataclass


def _load_dotenv(path: str = ".env") -> None:
    """Reads KEY=value lines from a local .env file, without overriding real env vars."""
    if not os.path.isfile(path):
        return
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            os.environ.setdefault(key.strip(), value.strip().strip('"').strip("'"))


@dataclass(frozen=True)
class Settings:
    store_api_url: str      # e.g. https://api.cauverystore.in  (the store backend)
    agents_token: str       # same value as AGENTS_TOKEN on the store backend
    ai_base_url: str        # any OpenAI-compatible endpoint (Gemini, Groq, OpenAI, Ollama...)
    ai_api_key: str
    ai_model: str
    batch_size: int         # how many jobs one run takes
    ai_timeout: int         # seconds to wait for one AI answer (a model on a plain CPU is slow)
    max_minutes: int        # stop starting new jobs after this long, so runs never overlap

    def missing(self) -> list[str]:
        names = {
            "STORE_API_URL": self.store_api_url,
            "AGENTS_TOKEN": self.agents_token,
            "AI_BASE_URL": self.ai_base_url,
            "AI_MODEL": self.ai_model,
        }
        return [k for k, v in names.items() if not v]


def load() -> Settings:
    _load_dotenv()
    def num(name: str, default: int) -> int:
        try:
            return int(os.environ.get(name, str(default)))
        except ValueError:
            return default
    batch = num("AGENT_BATCH_SIZE", 10)
    return Settings(
        store_api_url=os.environ.get("STORE_API_URL", "").rstrip("/"),
        agents_token=os.environ.get("AGENTS_TOKEN", ""),
        ai_base_url=os.environ.get("AI_BASE_URL", "").rstrip("/"),
        ai_api_key=os.environ.get("AI_API_KEY", ""),
        ai_model=os.environ.get("AI_MODEL", ""),
        batch_size=max(1, min(batch, 50)),
        ai_timeout=max(30, num("AI_TIMEOUT", 120)),
        max_minutes=max(1, num("AGENT_MAX_MINUTES", 45)),
    )
