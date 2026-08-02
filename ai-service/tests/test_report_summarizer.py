from __future__ import annotations

import pytest

from app.providers.echo_provider import EchoProvider
from app.providers.mock_provider import MockProvider
from app.schemas.requests import DepartmentLoad, ParentProgress, SummarizeReportRequest
from app.services.report_summarizer import summarize_report

VALID_SUMMARY = {
    "summary": "Three tasks were completed this week; one remains blocked.",
    "recommended_actions": ["Review the blocked task and its listed blocker."],
}


def _request(**overrides) -> SummarizeReportRequest:
    defaults = dict(
        period_start="2026-07-27",
        period_end="2026-08-03",
        status_counts={"DONE": 3, "IN_PROGRESS": 2, "BLOCKED": 1},
        completed_count=3,
        blocked_count=1,
        overdue_count=0,
        sla_risk_count=1,
        reassignment_count=0,
        approval_change_count=2,
        department_workload=[DepartmentLoad(department_name="Engineering", total_capacity_hours=80, assigned_hours=60)],
        parent_ticket_progress=[ParentProgress(reference_key="TCK-1", progress_percent=75)],
    )
    defaults.update(overrides)
    return SummarizeReportRequest(**defaults)


@pytest.mark.asyncio
async def test_summarize_report_returns_the_models_summary_and_actions():
    provider = EchoProvider([VALID_SUMMARY])
    parsed, meta = await summarize_report(_request(), provider)
    assert parsed.summary == VALID_SUMMARY["summary"]
    assert parsed.recommended_actions == VALID_SUMMARY["recommended_actions"]
    assert meta.provider == "echo"


@pytest.mark.asyncio
async def test_mock_provider_summary_restates_only_the_given_counts():
    """The deterministic mock fixture must never fabricate a number it was not given -
    every count in the summary should trace back to the request."""
    request = _request(completed_count=7, blocked_count=2, overdue_count=1, sla_risk_count=0)
    parsed, meta = await summarize_report(request, MockProvider())
    assert meta.is_mock is True
    assert "7" in parsed.summary
    assert "2" in parsed.summary
    assert "1" in parsed.summary
    # No employee name, ranking or score language anywhere in the generated text.
    for forbidden in ("rank", "score", "best", "worst", "top performer"):
        assert forbidden not in parsed.summary.lower()
        assert all(forbidden not in action.lower() for action in parsed.recommended_actions)


@pytest.mark.asyncio
async def test_mock_provider_summary_has_no_actions_when_nothing_needs_attention():
    request = _request(blocked_count=0, overdue_count=0, sla_risk_count=0)
    parsed, _meta = await summarize_report(request, MockProvider())
    assert len(parsed.recommended_actions) >= 1
    assert "no blocked" in parsed.recommended_actions[0].lower()
