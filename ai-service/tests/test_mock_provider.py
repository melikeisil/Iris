from __future__ import annotations

import json

import pytest

from app.providers.mock_provider import MockProvider
from app.schemas.responses import AnalysisResponse, DecomposeResponse


@pytest.mark.asyncio
async def test_mock_provider_deterministic():
    provider = MockProvider()
    raw1, _ = await provider.complete_structured("sys", "Users must be able to upload a profile photo", AnalysisResponse)
    raw2, _ = await provider.complete_structured("sys", "Users must be able to upload a profile photo", AnalysisResponse)
    assert raw1 == raw2


@pytest.mark.asyncio
async def test_mock_provider_output_is_schema_valid():
    provider = MockProvider()
    raw, meta = await provider.complete_structured("sys", "Prepare all onboarding activities for a new employee", AnalysisResponse)
    parsed = AnalysisResponse.model_validate_json(raw)
    assert parsed.work_family == "PROCESS_WORK"
    assert meta.is_mock is True


@pytest.mark.asyncio
async def test_mock_covers_onboarding_scenario():
    provider = MockProvider()
    raw, _ = await provider.complete_structured(
        "sys", "Prepare all onboarding activities for a new employee starting Monday", AnalysisResponse
    )
    analysis = AnalysisResponse.model_validate_json(raw)
    assert analysis.work_family == "PROCESS_WORK"
    assert analysis.priority == "HIGH"
    assert analysis.requires_more_information is True


@pytest.mark.asyncio
async def test_mock_covers_onboarding_scenario_after_clarification():
    provider = MockProvider()
    raw, _ = await provider.complete_structured(
        "sys",
        "Prepare onboarding. clarification_answers: role=Backend Engineer, start date=Monday",
        AnalysisResponse,
    )
    analysis = AnalysisResponse.model_validate_json(raw)
    assert analysis.matched_process_template == "EMPLOYEE_ONBOARDING"
    assert analysis.requires_decomposition is True

    raw_decomp, _ = await provider.complete_structured("sys", "decompose onboarding", DecomposeResponse)
    decomposition = DecomposeResponse.model_validate_json(raw_decomp)
    assert len(decomposition.suggested_work_items) == 9


@pytest.mark.asyncio
async def test_mock_covers_development_story_scenario():
    provider = MockProvider()
    raw, _ = await provider.complete_structured(
        "sys", "Users must be able to upload a profile photo", AnalysisResponse
    )
    analysis = AnalysisResponse.model_validate_json(raw)
    assert analysis.work_item_type == "STORY"
    assert analysis.work_family == "DEVELOPMENT_WORK"
    assert analysis.confidence == pytest.approx(0.52)
    assert analysis.requires_more_information is True

    raw_answered, _ = await provider.complete_structured(
        "sys", "Users must be able to upload a profile photo. jpeg/png, 5mb limit, no migration needed", AnalysisResponse
    )
    answered = AnalysisResponse.model_validate_json(raw_answered)
    assert answered.confidence == pytest.approx(0.87)


@pytest.mark.asyncio
async def test_mock_covers_defect_detection_scenario():
    provider = MockProvider()
    raw, _ = await provider.complete_structured(
        "sys", "Payments fail intermittently during checkout", AnalysisResponse
    )
    analysis = AnalysisResponse.model_validate_json(raw)
    assert analysis.likely_defect is True
    assert analysis.suggested_conversion == "BUG"
    assert analysis.production_impact is True


@pytest.mark.asyncio
async def test_mock_fallback_handles_unscripted_input():
    provider = MockProvider()
    raw, _ = await provider.complete_structured("sys", "The printer on the third floor is jammed again", AnalysisResponse)
    analysis = AnalysisResponse.model_validate_json(raw)
    assert analysis.confidence < 0.6  # low-confidence generic fallback, not a scripted fixture


@pytest.mark.asyncio
async def test_mock_provider_simulates_realistic_latency():
    provider = MockProvider()
    _, meta = await provider.complete_structured("sys", "anything", AnalysisResponse)
    assert meta.latency_ms >= 700  # ~800ms simulated, contract §7
