"""Real provider — contract §7. Tool-schema-constrained output derived from the Pydantic
model's own JSON schema, so the model is structurally steered toward valid output before
Pydantic ever gets to reject anything. Retries once on 429/5xx with jitter (`tenacity`);
the separate *validation* repair retry in `analyzer.py` is a different concern and is not
duplicated here.
"""

from __future__ import annotations

import time

from anthropic import AsyncAnthropic
from pydantic import BaseModel
from tenacity import retry, retry_if_exception, stop_after_attempt, wait_random_exponential

from app.config import settings
from app.schemas.responses import ProviderMeta

_TOOL_NAME = "emit_structured_output"


def _is_retryable(exc: BaseException) -> bool:
    status = getattr(exc, "status_code", None)
    return status == 429 or (isinstance(status, int) and status >= 500)


class AnthropicProvider:
    name = "anthropic"
    is_mock = False

    def __init__(self, client: AsyncAnthropic | None = None) -> None:
        self.model = settings.anthropic_model
        self._client = client or AsyncAnthropic(
            api_key=settings.anthropic_api_key,
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
        schema = response_schema.model_json_schema()
        # Anthropic's structured-output tool call: the schema itself becomes the tool's
        # input contract, so a well-formed call already satisfies most field-level rules.
        tool = {
            "name": _TOOL_NAME,
            "description": f"Emit a {response_schema.__name__} conforming to the given schema.",
            "input_schema": schema,
        }

        start = time.perf_counter()
        message = await self._call_with_retry(
            system_prompt=system_prompt,
            user_content=user_content,
            tool=tool,
            max_tokens=max_tokens,
            temperature=temperature,
        )
        latency_ms = int((time.perf_counter() - start) * 1000)

        tool_use = next((b for b in message.content if b.type == "tool_use"), None)
        if tool_use is None:
            raise RuntimeError("Anthropic response contained no tool_use block")

        import json as _json

        raw = _json.dumps(tool_use.input)
        meta = ProviderMeta(
            provider=self.name,
            model=self.model,
            latency_ms=latency_ms,
            input_tokens=message.usage.input_tokens,
            output_tokens=message.usage.output_tokens,
            is_mock=False,
            prompt_version=settings.prompt_version,
        )
        return raw, meta

    @retry(
        retry=retry_if_exception(_is_retryable),
        stop=stop_after_attempt(1 + max(settings.llm_max_retries, 0)),
        wait=wait_random_exponential(multiplier=0.5, max=5),
        reraise=True,
    )
    async def _call_with_retry(
        self, *, system_prompt: str, user_content: str, tool: dict, max_tokens: int, temperature: float
    ):
        return await self._client.messages.create(
            model=self.model,
            max_tokens=max_tokens,
            temperature=temperature,
            system=system_prompt,
            tools=[tool],
            tool_choice={"type": "tool", "name": _TOOL_NAME},
            messages=[{"role": "user", "content": user_content}],
        )

    async def is_reachable(self) -> bool:
        """Startup probe used by `factory.py` when LLM_PROVIDER=auto."""
        try:
            await self._client.models.list(limit=1)
            return True
        except Exception:
            return False
