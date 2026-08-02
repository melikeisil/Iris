"""Test-only provider — contract §7. Returns a fixture supplied by the caller, used to
drive validation-failure and repair-loop tests deterministically without depending on
`MockProvider`'s scenario-matching logic."""

from __future__ import annotations

import json
import time
from collections.abc import Sequence

from pydantic import BaseModel

from app.schemas.responses import ProviderMeta


class EchoProvider:
    name = "echo"
    model = "echo-test"
    is_mock = True

    def __init__(self, responses: Sequence[str | dict]) -> None:
        """`responses` is consumed in order, one per call — e.g. `[invalid_json, valid_json]`
        to drive a repair-loop-succeeds test, or `[invalid, invalid]` for repair-gives-up."""
        self._responses = list(responses)
        self._calls = 0

    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[BaseModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[str, ProviderMeta]:
        if self._calls >= len(self._responses):
            raise AssertionError("EchoProvider ran out of scripted responses")
        item = self._responses[self._calls]
        self._calls += 1
        raw = item if isinstance(item, str) else json.dumps(item)
        meta = ProviderMeta(
            provider=self.name,
            model=self.model,
            latency_ms=1,
            input_tokens=0,
            output_tokens=0,
            is_mock=True,
            prompt_version="v1",
        )
        return raw, meta

    @property
    def call_count(self) -> int:
        return self._calls
