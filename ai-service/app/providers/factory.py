"""Provider selection — contract §7 and §10.

`auto` (the default) prefers Gemini, then Anthropic, when a corresponding API key is
present *and* reachable at startup, otherwise `mock`, and logs the choice loudly — this
is the single mechanism that lets `docker compose up` demo the whole product with zero
external credentials.
"""

from __future__ import annotations

import logging

from app.config import ProviderChoice, settings
from app.providers.anthropic_provider import AnthropicProvider
from app.providers.base import LLMProvider
from app.providers.gemini_provider import GeminiProvider
from app.providers.mock_provider import MockProvider

logger = logging.getLogger("smartwork.ai.provider")


async def select_provider() -> LLMProvider:
    choice = settings.llm_provider

    if choice == ProviderChoice.MOCK:
        logger.info("provider_selected", extra={"provider": "mock", "reason": "forced by LLM_PROVIDER=mock"})
        return MockProvider()

    if choice == ProviderChoice.ANTHROPIC:
        if not settings.anthropic_api_key:
            raise RuntimeError("LLM_PROVIDER=anthropic but ANTHROPIC_API_KEY is not set")
        logger.info("provider_selected", extra={"provider": "anthropic", "reason": "forced by LLM_PROVIDER=anthropic"})
        return AnthropicProvider()

    if choice == ProviderChoice.GEMINI:
        if not settings.gemini_api_key:
            raise RuntimeError("LLM_PROVIDER=gemini but GEMINI_API_KEY is not set")
        logger.info("provider_selected", extra={"provider": "gemini", "reason": "forced by LLM_PROVIDER=gemini"})
        return GeminiProvider()

    # auto
    if settings.gemini_api_key:
        candidate = GeminiProvider()
        if await candidate.is_reachable():
            logger.info("provider_selected", extra={"provider": "gemini", "reason": "key present and reachable"})
            return candidate
        logger.warning(
            "provider_probe_failed",
            extra={"provider": "gemini", "reason": "GEMINI_API_KEY set but provider unreachable at startup"},
        )

    if settings.anthropic_api_key:
        candidate = AnthropicProvider()
        if await candidate.is_reachable():
            logger.info("provider_selected", extra={"provider": "anthropic", "reason": "key present and reachable"})
            return candidate
        logger.warning(
            "provider_probe_failed",
            extra={"provider": "anthropic", "reason": "ANTHROPIC_API_KEY set but provider unreachable at startup"},
        )

    if not settings.gemini_api_key and not settings.anthropic_api_key:
        logger.warning(
            "provider_selected",
            extra={"provider": "mock", "reason": "no provider key configured; falling back to mock"},
        )
        return MockProvider()

    logger.warning(
        "provider_selected",
        extra={"provider": "mock", "reason": "provider key configured but no provider reachable at startup"},
    )
    return MockProvider()
