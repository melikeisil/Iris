from __future__ import annotations

import httpx
import pytest
from pydantic import BaseModel

from app.config import settings
from app.providers.echo_provider import EchoProvider
from app.schemas.enums import OriginType
from app.schemas.requests import AnalyzeTicketRequest
from app.services.analyzer import AnalysisFailedError, analyze_ticket

VALID_ANALYSIS = {
    "work_family": "SERVICE_WORK",
    "work_item_type": "REQUEST",
    "category": "general",
    "summary": "Reset a locked account",
    "project_code": None,
    "recommended_team": None,
    "departments": [{"department_code": "IT", "confidence": 0.9, "reason": "IT owns account access."}],
    "affected_components": [],
    "required_skills": ["USER_PROVISIONING"],
    "priority": "MEDIUM",
    "priority_score": 40,
    "risk_level": "LOW",
    "risk_signals": [],
    "complexity": "LOW",
    "suggested_story_points": None,
    "requires_more_information": False,
    "clarification_questions": [],
    "requires_decomposition": False,
    "matched_process_template": None,
    "estimated_total_hours": None,
    "confidence": 0.8,
    "suggested_work_items": [],
    "explanation": "Standard account-unlock request with no ambiguity.",
}

INVALID_JSON = "{not valid json"
TRUNCATED_JSON = '{"work_family":"SERVICE_WORK","summary":"cut off'


def _request() -> AnalyzeTicketRequest:
    return AnalyzeTicketRequest(
        correlation_id="corr-1",
        work_item_id="wi-1",
        summary="My account is locked and I cannot log in",
        origin_type=OriginType.USER_CREATED,
        org_context={
            "department_codes": ["IT", "ENGINEERING"],
            "team_codes": [],
            "project_codes": [],
            "component_codes": [],
            "skill_codes": ["USER_PROVISIONING"],
            "process_template_codes": [],
            "category_codes": [],
        },
    )


class RecordingProvider:
    name = "recording"
    model = "recording-test"
    is_mock = True

    def __init__(self, responses: list[str | dict]) -> None:
        self._provider = EchoProvider(responses)
        self.user_contents: list[str] = []
        self.temperatures: list[float] = []

    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[BaseModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[str, object]:
        self.user_contents.append(user_content)
        self.temperatures.append(temperature)
        return await self._provider.complete_structured(
            system_prompt,
            user_content,
            response_schema,
            max_tokens,
            temperature,
        )


class FailingProvider:
    name = "gemini"
    model = "gemini-3.5-flash"
    is_mock = False

    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[BaseModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[str, object]:
        request = httpx.Request("POST", "https://example.test/models/gemini:generateContent")
        response = httpx.Response(429, request=request)
        raise httpx.HTTPStatusError("rate limited", request=request, response=response)


@pytest.mark.asyncio
async def test_repair_loop_succeeds_on_second_attempt():
    provider = EchoProvider([INVALID_JSON, VALID_ANALYSIS])
    envelope = await analyze_ticket(_request(), provider)
    assert envelope.meta.attempts == 2
    assert envelope.analysis.summary == VALID_ANALYSIS["summary"]
    assert provider.call_count == 2


@pytest.mark.asyncio
async def test_repair_loop_gives_up_after_one_retry():
    provider = EchoProvider([INVALID_JSON, INVALID_JSON])
    with pytest.raises(AnalysisFailedError) as excinfo:
        await analyze_ticket(_request(), provider)
    assert excinfo.value.attempts == 2
    assert provider.call_count == 2


@pytest.mark.asyncio
async def test_repair_loop_succeeds_immediately_when_first_attempt_is_valid():
    provider = EchoProvider([VALID_ANALYSIS])
    envelope = await analyze_ticket(_request(), provider)
    assert envelope.meta.attempts == 1
    assert provider.call_count == 1


@pytest.mark.asyncio
async def test_repair_loop_treats_schema_violation_same_as_parse_failure():
    """A structurally-valid-JSON-but-schema-invalid first attempt (missing a required
    field) should still trigger exactly one repair attempt, same as malformed JSON."""
    broken = {**VALID_ANALYSIS}
    del broken["explanation"]
    provider = EchoProvider([broken, VALID_ANALYSIS])
    envelope = await analyze_ticket(_request(), provider)
    assert envelope.meta.attempts == 2


@pytest.mark.asyncio
async def test_truncated_json_retry_regenerates_from_original_request():
    provider = RecordingProvider([TRUNCATED_JSON, VALID_ANALYSIS])
    envelope = await analyze_ticket(_request(), provider)
    assert envelope.meta.attempts == 2
    assert "Previous output was truncated or malformed JSON" in provider.user_contents[1]
    assert "## Your previous output" not in provider.user_contents[1]
    assert provider.temperatures == [0.2, 0.0]


@pytest.mark.asyncio
async def test_runtime_provider_failure_falls_back_to_mock_provider():
    previous = settings.demo_mode
    settings.demo_mode = True
    try:
        envelope = await analyze_ticket(_request(), FailingProvider())
    finally:
        settings.demo_mode = previous
    assert envelope.meta.provider == "demo-fallback"
    assert envelope.meta.is_mock is True
    assert envelope.analysis.summary


@pytest.mark.asyncio
async def test_runtime_provider_failure_does_not_fallback_outside_demo_mode():
    previous = settings.demo_mode
    settings.demo_mode = False
    try:
        with pytest.raises(httpx.HTTPStatusError):
            await analyze_ticket(_request(), FailingProvider())
    finally:
        settings.demo_mode = previous


@pytest.mark.asyncio
async def test_non_429_provider_failure_is_not_hidden_by_demo_mode():
    class ServerErrorProvider(FailingProvider):
        async def complete_structured(
            self,
            system_prompt: str,
            user_content: str,
            response_schema: type[BaseModel],
            max_tokens: int = 4096,
            temperature: float = 0.2,
        ) -> tuple[str, object]:
            request = httpx.Request("POST", "https://example.test/models/gemini:generateContent")
            response = httpx.Response(503, request=request)
            raise httpx.HTTPStatusError("upstream error", request=request, response=response)

    previous = settings.demo_mode
    settings.demo_mode = True
    try:
        with pytest.raises(httpx.HTTPStatusError):
            await analyze_ticket(_request(), ServerErrorProvider())
    finally:
        settings.demo_mode = previous
