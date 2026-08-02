from __future__ import annotations

import pytest
from pydantic import ValidationError

from app.schemas.enums import WorkFamily
from app.schemas.responses import SuggestedWorkItem
from app.schemas.validators import (
    DecompositionValidationError,
    validate_decomposition_set,
    validate_dependency_graph_acyclic,
    validate_dependencies_reference_existing_items,
)


def _item(**overrides) -> SuggestedWorkItem:
    base = dict(
        temp_id="t1",
        title="Add POST /profile/avatar endpoint with validation",
        description="Implements the avatar upload endpoint with size and MIME validation.",
        work_item_type="TASK",
        department_code="ENGINEERING",
        team_code="BACKEND",
        required_skills=["SPRING_BOOT"],
        estimated_hours=8.0,
        priority="MEDIUM",
        risk_level="LOW",
        dependencies=[],
        closure_type="AUTO_CLOSE",
        confidence=0.8,
        explanation="Standard backend slice with clear scope and acceptance criteria.",
    )
    base.update(overrides)
    return SuggestedWorkItem.model_validate(base)


@pytest.mark.parametrize(
    "title",
    [
        "Work on backend",
        "Fix the bug",
        "Backend work",
        "Investigate this",
        "Do it",
    ],
)
def test_vague_titles_rejected(title: str):
    with pytest.raises(ValidationError):
        _item(title=title)


def test_good_title_passes():
    _item(title="Add POST /profile/avatar endpoint with validation")


def test_dependency_graph_acyclic_detects_cycle():
    items = [_item(temp_id="a", dependencies=["b"]), _item(temp_id="b", dependencies=["a"])]
    with pytest.raises(DecompositionValidationError):
        validate_dependency_graph_acyclic(items)


def test_dependency_graph_accepts_dag():
    items = [
        _item(temp_id="a", dependencies=[]),
        _item(temp_id="b", dependencies=["a"]),
        _item(temp_id="c", dependencies=["a", "b"]),
    ]
    validate_dependency_graph_acyclic(items)  # does not raise


def test_dependencies_must_reference_existing_items():
    items = [_item(temp_id="a", dependencies=["ghost"])]
    with pytest.raises(DecompositionValidationError):
        validate_dependencies_reference_existing_items(items)


def test_vocabulary_enforcement_rejects_unknown_department(org_context):
    items = [_item(department_code="NOT_A_REAL_DEPARTMENT")]
    with pytest.raises(DecompositionValidationError):
        validate_decomposition_set(items, org_context, WorkFamily.DEVELOPMENT_WORK, None)


def test_team_must_belong_to_named_department(org_context):
    # BACKEND belongs to ENGINEERING, not HR.
    items = [_item(department_code="HR", team_code="BACKEND", testing_requirements=["covered"])]
    with pytest.raises(DecompositionValidationError):
        validate_decomposition_set(items, org_context, WorkFamily.DEVELOPMENT_WORK, None)


def test_development_work_requires_testing_coverage(org_context):
    items = [_item(testing_requirements=[])]
    with pytest.raises(DecompositionValidationError):
        validate_decomposition_set(items, org_context, WorkFamily.DEVELOPMENT_WORK, None)


def test_development_work_satisfied_by_a_test_item(org_context):
    items = [
        _item(temp_id="a", testing_requirements=[]),
        _item(temp_id="b", work_item_type="TEST", testing_requirements=[], title="Write integration test suite"),
    ]
    validate_decomposition_set(items, org_context, WorkFamily.DEVELOPMENT_WORK, None)  # does not raise


def test_hours_budget_mismatch_is_a_warning_not_a_failure(org_context):
    items = [_item(estimated_hours=80.0, testing_requirements=["covered"])]
    warnings = validate_decomposition_set(items, org_context, WorkFamily.DEVELOPMENT_WORK, 10.0)
    assert warnings, "expected a budget warning"
