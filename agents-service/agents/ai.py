"""The AI "brain" plug. Speaks the OpenAI-compatible chat format that most providers offer, so
the provider is chosen only by AI_BASE_URL / AI_API_KEY / AI_MODEL:

    Google Gemini  https://generativelanguage.googleapis.com/v1beta/openai
    Groq           https://api.groq.com/openai/v1
    OpenAI         https://api.openai.com/v1
    OpenRouter     https://openrouter.ai/api/v1
    Ollama (own)   http://localhost:11434/v1
"""

import json
import re

from .http import HttpError, request_json


class AiError(Exception):
    pass


class AiClient:
    def __init__(self, base_url: str, api_key: str, model: str, timeout: int = 120):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    def complete_json(self, system: str, user: str) -> dict:
        try:
            text = self._complete(system, user, json_mode=True)
        except HttpError as e:
            if e.status == 400 and "response_format" in e.body:
                text = self._complete(system, user, json_mode=False)
            else:
                raise AiError(self._explain(e)) from None
        return parse_json_object(text)

    def _complete(self, system: str, user: str, json_mode: bool) -> str:
        body = {
            "model": self.model,
            "temperature": 0.4,
            "messages": [
                {"role": "system", "content": system},
                {"role": "user", "content": user},
            ],
        }
        if json_mode:
            body["response_format"] = {"type": "json_object"}
        headers = {"Authorization": f"Bearer {self.api_key}"} if self.api_key else {}
        res = request_json("POST", f"{self.base_url}/chat/completions", body, headers, timeout=self.timeout)
        try:
            content = res["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError):
            raise AiError("The AI service sent an answer in an unexpected form.") from None
        if not content or not str(content).strip():
            raise AiError("The AI service sent an empty answer.")
        return str(content)

    @staticmethod
    def _explain(e: HttpError) -> str:
        if e.status in (401, 403):
            return "The AI service refused the key (check AI_API_KEY)."
        if e.status == 429:
            return "The AI service is busy or its free limit is used up."
        return f"The AI service returned an error ({e.status})."


def parse_json_object(text: str) -> dict:
    """Pulls the JSON object out of an answer, even if it is wrapped in code fences or prose."""
    text = re.sub(r"(?s)<think>.*?</think>", "", text)  # open models may "think" aloud first
    cleaned = re.sub(r"^```[a-zA-Z]*\s*|\s*```\s*$", "", text.strip())
    start, end = cleaned.find("{"), cleaned.rfind("}")
    if start < 0 or end <= start:
        raise AiError("The AI answer did not contain a JSON object.")
    try:
        value = json.loads(cleaned[start:end + 1])
    except json.JSONDecodeError:
        raise AiError("The AI answer was not valid JSON.") from None
    if not isinstance(value, dict):
        raise AiError("The AI answer was not a JSON object.")
    return value
