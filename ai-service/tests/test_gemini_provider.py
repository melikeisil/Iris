from __future__ import annotations

import asyncio
import json

import httpx
import pytest

from app.config import settings
from app.providers.gemini_provider import GeminiProvider
from app.schemas.responses import AnalysisResponse


class StubClient:
    def __init__(self, responses: list[httpx.Response | Exception]) -> None:
        self._responses = responses
        self.calls = 0

    async def post(self, path: str, json: dict) -> httpx.Response:
        response = self._responses[self.calls]
        self.calls += 1
        if isinstance(response, Exception):
            raise response
        return response


def _success_response() -> httpx.Response:
    body = {
        "candidates": [
            {
                "content": {
                    "parts": [
                        {
                            "text": json.dumps(
                                {
                                    "work_family": "SERVICE_WORK",
                                    "work_item_type": "REQUEST",
                                    "category": "general",
                                    "summary": "Reset a locked account",
                                    "project_code": None,
                                    "recommended_team": None,
                                    "departments": [
                                        {
                                            "department_code": "IT",
                                            "confidence": 0.9,
                                            "reason": "IT owns account access.",
                                        }
                                    ],
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
                                    "security_impact": False,
                                    "production_impact": False,
                                    "budget_impact": False,
                                    "compliance_impact": False,
                                    "sla_risk": "NONE",
                                    "business_impact": "NONE",
                                    "likely_duplicate_of": None,
                                    "likely_defect": False,
                                    "suggested_conversion": None,
                                    "injection_suspected": False,
                                    "explanation": "Standard account-unlock request with no ambiguity.",
                                }
                            )
                        }
                    ]
                }
            }
        ],
        "usageMetadata": {"promptTokenCount": 12, "candidatesTokenCount": 34},
    }
    return httpx.Response(200, json=body, request=httpx.Request("POST", "https://example.test"))


@pytest.mark.asyncio
async def test_gemini_provider_retries_429_and_respects_retry_after(monkeypatch: pytest.MonkeyPatch):
    previous = settings.llm_max_retries
    settings.llm_max_retries = 2
    sleeps: list[float] = []

    async def fake_sleep(delay: float) -> None:
        sleeps.append(delay)

    monkeypatch.setattr(asyncio, "sleep", fake_sleep)

    retry_response = httpx.Response(
        429,
        headers={"Retry-After": "0.25"},
        request=httpx.Request("POST", "https://example.test"),
    )
    client = StubClient([retry_response, _success_response()])
    provider = GeminiProvider(client=client)

    try:
        raw, meta = await provider.complete_structured("system", "user", AnalysisResponse)
    finally:
        settings.llm_max_retries = previous

    assert client.calls == 2
    assert sleeps == [0.25]
    assert meta.provider == "gemini"
    assert json.loads(raw)["summary"] == "Reset a locked account"


@pytest.mark.asyncio
async def test_gemini_provider_stops_after_maximum_two_retries(monkeypatch: pytest.MonkeyPatch):
    previous = settings.llm_max_retries
    settings.llm_max_retries = 2
    sleeps: list[float] = []

    async def fake_sleep(delay: float) -> None:
        sleeps.append(delay)

    monkeypatch.setattr(asyncio, "sleep", fake_sleep)

    first = httpx.Response(429, request=httpx.Request("POST", "https://example.test"))
    second = httpx.Response(429, request=httpx.Request("POST", "https://example.test"))
    third = httpx.Response(429, request=httpx.Request("POST", "https://example.test"))
    client = StubClient([first, second, third])
    provider = GeminiProvider(client=client)

    try:
        with pytest.raises(httpx.HTTPStatusError):
            await provider.complete_structured("system", "user", AnalysisResponse)
    finally:
        settings.llm_max_retries = previous

    assert client.calls == 3
    assert sleeps == [0.5, 1.0]
