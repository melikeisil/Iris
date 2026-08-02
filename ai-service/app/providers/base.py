"""The provider abstraction — contract §7.

`complete_structured` returns a *validated* Pydantic model on success. On failure (JSON
parse error, schema violation, or a post-validator in `services/analyzer.py` rejecting the
result) it raises `StructuredCompletionError` carrying the raw text and the exact error
list, which `analyzer.py` feeds into exactly one repair attempt (contract §8). Both the
mock and the real provider are exercised through the same validation path — a provider
never validates its own output beyond "is this parseable JSON", so a bug in mock-vs-real
divergence would surface as a schema failure, not a silent behavioural difference.
"""

from __future__ import annotations

from typing import Protocol, TypeVar

from pydantic import BaseModel

from app.schemas.responses import ProviderMeta

TModel = TypeVar("TModel", bound=BaseModel)


class StructuredCompletionError(Exception):
    """Raised when a provider's output fails to parse as JSON. Schema-level validation
    failures are raised by the caller (`analyzer.py`) after parsing, not here, so that
    validation logic never lives twice."""

    def __init__(self, raw_output: str, message: str) -> None:
        super().__init__(message)
        self.raw_output = raw_output
        self.message = message


class LLMProvider(Protocol):
    name: str
    model: str
    is_mock: bool

    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[TModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[str, ProviderMeta]:
        """Return the raw JSON text the model produced, plus provenance metadata.
        Intentionally returns text rather than a parsed model: parsing and schema
        validation are `analyzer.py`'s job, so the repair loop is provider-agnostic."""
        ...
