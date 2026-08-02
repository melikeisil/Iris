"""The structural guarantee from contract §2 and §6: 'AnalysisResponse has no
assigneeUserId.' Candidate ranking and routing happen in Java, on stored data, with a
deterministic formula — the model never sees the employee list and this test is what
keeps that true even as the schema evolves.
"""

from __future__ import annotations

import re

from app.schemas.requests import SummarizeReportRequest
from app.schemas.responses import (
    AnalysisResponse,
    DecomposeResponse,
    ExtractSkillsResponse,
    ReportSummaryResponse,
    SuggestedWorkItem,
)

_FORBIDDEN = re.compile(r"assignee|(?<!self_)user(?!name)|approve|authoriz|^route$", re.IGNORECASE)

# The weekly report brief is explicit: no employee ranking or performance score, ever.
# Checked on both sides of the report summary call - the request the backend builds (so
# a future change can't start sending per-employee figures) and the response the model
# returns (so a future change can't start asking for one back).
_FORBIDDEN_RANKING = re.compile(
    r"rank|score|performance|top_|best_|worst_|employee_name|assignee_name", re.IGNORECASE
)

# Fields that legitimately contain one of the forbidden substrings without being an
# authorization decision — e.g. "required_skills" contains no forbidden word, but keep an
# explicit allowlist so a future rename doesn't get silently waved through.
_ALLOWED = {"unresolved_skills"}


def _assert_no_forbidden_fields(model_cls) -> None:
    for name in model_cls.model_fields:
        if name in _ALLOWED:
            continue
        assert not _FORBIDDEN.search(name), f"{model_cls.__name__}.{name} looks like an authorization field"


def test_analysis_response_has_no_authorization_fields():
    _assert_no_forbidden_fields(AnalysisResponse)


def test_suggested_work_item_has_no_authorization_fields():
    _assert_no_forbidden_fields(SuggestedWorkItem)


def test_decompose_response_has_no_authorization_fields():
    _assert_no_forbidden_fields(DecomposeResponse)


def test_extract_skills_response_has_no_authorization_fields():
    _assert_no_forbidden_fields(ExtractSkillsResponse)


def test_no_status_field_that_the_backend_would_apply_blindly():
    """Contract §2: 'its responses contain no status field the backend applies blindly.'"""
    for model_cls in (AnalysisResponse, SuggestedWorkItem, DecomposeResponse):
        assert "status" not in model_cls.model_fields


def test_report_summary_response_has_no_authorization_fields():
    _assert_no_forbidden_fields(ReportSummaryResponse)


def test_report_summary_request_and_response_have_no_ranking_or_scoring_fields():
    """The Manager weekly report must not rank employees or generate performance scores
    (see the report brief). Enforced on the request the backend builds - so the input the
    model sees can never carry a per-employee figure - and on the response schema, so a
    future change cannot add a field for the model to fill one in."""
    for model_cls in (SummarizeReportRequest, ReportSummaryResponse):
        for name in model_cls.model_fields:
            assert not _FORBIDDEN_RANKING.search(name), (
                f"{model_cls.__name__}.{name} looks like an employee ranking/scoring field"
            )
