"""Gemini provider using the Google Gemini REST API.

The service already validates the model output with Pydantic and performs one repair
attempt centrally in `analyzer.py`, so this provider's responsibilities stay narrow:
send a structured-output request, extract the returned JSON text, and surface transport
errors with enough signal for the shared retry logic.
"""

from __future__ import annotations

import asyncio
import time
from typing import Any

import httpx
from pydantic import BaseModel

from app.config import settings
from app.schemas.responses import ProviderMeta

_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
_ALLOWED_SCHEMA_KEYS = {
    "$id",
    "$defs",
    "$ref",
    "$anchor",
    "type",
    "format",
    "title",
    "description",
    "enum",
    "items",
    "prefixItems",
    "minItems",
    "maxItems",
    "minimum",
    "maximum",
    "anyOf",
    "oneOf",
    "properties",
    "additionalProperties",
    "required",
    "propertyOrdering",
}


def _prune_schema(value: Any) -> Any:
    if isinstance(value, dict):
        pruned: dict[str, Any] = {}
        for key, item in value.items():
            if key not in _ALLOWED_SCHEMA_KEYS:
                continue
            if key in {"properties", "$defs"} and isinstance(item, dict):
                pruned[key] = {name: _prune_schema(schema) for name, schema in item.items()}
            else:
                pruned[key] = _prune_schema(item)
        return pruned
    if isinstance(value, list):
        return [_prune_schema(item) for item in value]
    return value


class GeminiProvider:
    name = "gemini"
    is_mock = False

    def __init__(self, client: httpx.AsyncClient | None = None) -> None:
        self.model = settings.gemini_model
        self._client = client or httpx.AsyncClient(
            base_url=_BASE_URL,
            headers={"x-goog-api-key": settings.gemini_api_key},
            timeout=settings.llm_timeout_seconds,
        )

    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[BaseModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[str, ProviderMeta]:
        payload = {
            "systemInstruction": {
                "parts": [{"text": system_prompt}],
            },
            "contents": [
                {
                    "role": "user",
                    "parts": [{"text": user_content}],
                }
            ],
            "generationConfig": {
                "temperature": temperature,
                "maxOutputTokens": max_tokens,
                "responseMimeType": "application/json",
                "responseJsonSchema": _prune_schema(response_schema.model_json_schema()),
            },
        }

        start = time.perf_counter()
        response = await self._call_with_retry(payload)
        latency_ms = int((time.perf_counter() - start) * 1000)
        body = response.json()
        raw = self._extract_text(body)
        usage = body.get("usageMetadata", {})

        meta = ProviderMeta(
            provider=self.name,
            model=self.model,
            latency_ms=latency_ms,
            input_tokens=usage.get("promptTokenCount"),
            output_tokens=usage.get("candidatesTokenCount"),
            is_mock=False,
            prompt_version=settings.prompt_version,
        )
        return raw, meta

    async def _call_with_retry(self, payload: dict[str, Any]) -> httpx.Response:
        attempts = 1 + max(settings.llm_max_retries, 0)
        last_error: Exception | None = None

        for attempt in range(1, attempts + 1):
            try:
                response = await self._client.post(
                    f"/models/{self.model}:generateContent",
                    json=payload,
                )
                response.raise_for_status()
                return response
            except httpx.HTTPStatusError as exc:
                last_error = exc
                status = exc.response.status_code
                if status == 429 and attempt < attempts:
                    await asyncio.sleep(self._retry_delay_seconds(exc.response, attempt))
                    continue
                if status >= 500 and attempt < attempts:
                    await asyncio.sleep(self._retry_delay_seconds(exc.response, attempt))
                    continue
                raise
            except httpx.TransportError as exc:
                last_error = exc
                if attempt < attempts:
                    await asyncio.sleep(self._retry_delay_seconds(None, attempt))
                    continue
                raise

        assert last_error is not None
        raise last_error

    def _retry_delay_seconds(self, response: httpx.Response | None, attempt: int) -> float:
        if response is not None:
            retry_after = response.headers.get("Retry-After")
            if retry_after:
                try:
                    return max(0.0, float(retry_after))
                except ValueError:
                    pass
        # Limited exponential backoff: 0.5s, 1.0s, then stop.
        return min(0.5 * (2 ** max(attempt - 1, 0)), 1.0)

    def _extract_text(self, body: dict[str, Any]) -> str:
        candidates = body.get("candidates") or []
        if not candidates:
            prompt_feedback = body.get("promptFeedback", {})
            block_reason = prompt_feedback.get("blockReason")
            raise RuntimeError(
                "Gemini returned no candidates"
                + (f" (blockReason={block_reason})" if block_reason else "")
            )

        parts = candidates[0].get("content", {}).get("parts", [])
        text = "".join(part.get("text", "") for part in parts if isinstance(part, dict))
        if not text:
            raise RuntimeError("Gemini response contained no text part")
        return text

    async def is_reachable(self) -> bool:
        try:
            response = await self._client.post(
                f"/models/{self.model}:generateContent",
                json={
                    "contents": [{"parts": [{"text": "ping"}]}],
                    "generationConfig": {"maxOutputTokens": 1, "temperature": 0},
                },
            )
            response.raise_for_status()
            return True
        except Exception:
            return False
