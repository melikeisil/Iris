from __future__ import annotations

import json

import pytest
from pydantic import ValidationError

from app.schemas.responses import AnalysisResponse
from app.services import prompt_builder

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


def test_analysis_schema_rejects_extra_fields():
    payload = {**VALID_ANALYSIS, "assignee_user_id": "11111111-1111-1111-1111-111111111111"}
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


def test_valid_analysis_parses():
    AnalysisResponse.model_validate(VALID_ANALYSIS)


def test_clarification_consistency_requires_questions():
    payload = {**VALID_ANALYSIS, "requires_more_information": True, "clarification_questions": []}
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


def test_clarification_consistency_forbids_questions_when_not_required():
    payload = {
        **VALID_ANALYSIS,
        "requires_more_information": False,
        "clarification_questions": [
            {"id": "q1", "question": "What environment does this affect?", "field": "environment", "required": True}
        ],
    }
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


def test_critical_priority_requires_high_score():
    payload = {**VALID_ANALYSIS, "priority": "CRITICAL", "priority_score": 10}
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


def test_critical_risk_requires_signals():
    payload = {**VALID_ANALYSIS, "risk_level": "CRITICAL", "risk_signals": []}
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


def test_story_points_only_on_development_work():
    payload = {**VALID_ANALYSIS, "work_family": "SERVICE_WORK", "suggested_story_points": 5}
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


def test_story_points_must_be_fibonacci():
    payload = {**VALID_ANALYSIS, "work_family": "DEVELOPMENT_WORK", "suggested_story_points": 4}
    with pytest.raises(ValidationError):
        AnalysisResponse.model_validate(payload)


@pytest.mark.parametrize(
    "filename",
    [p.name for p in prompt_builder.all_example_files()],
)
def test_examples_validate_against_their_own_schema(filename: str):
    """Every few-shot example is version-controlled and must itself be schema-valid — a
    broken example would otherwise silently teach the model a broken shape (contract §9)."""
    data = json.loads(prompt_builder.example_path(filename).read_text())
    good_output = data["good_output"]
    if "suggested_work_items" in good_output and "work_family" in good_output:
        AnalysisResponse.model_validate(good_output)
    elif "suggested_work_items" in good_output:
        from app.schemas.responses import DecomposeResponse

        DecomposeResponse.model_validate({"work_item_id": "wi-1", **good_output})
    else:
        pytest.fail(f"{filename}: unrecognized example shape")
