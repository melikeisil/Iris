"""Weekly report summarization — the Manager report's "AI-generated management summary".

Reuses `analyzer._run_with_runtime_fallback` rather than re-implementing the repair loop,
the 429/demo-fallback handling, or the retry/backoff behaviour: a report summary call is
just another structured completion, and it should degrade exactly the same way an analysis
call does (see `test_repair_loop.py` and `test_gemini_provider.py` for what that already
covers).

The one thing genuinely specific to this endpoint is the prompt, and the constraint it
carries: the model restates the numbers it is given and nothing else. No employee names,
individual task descriptions, or per-person counts are in the input at all (see
`SummarizeReportRequest` — the backend only sends aggregate figures), so there is
structurally nothing for the model to rank or score even if the prompt were ignored.
"""

from __future__ import annotations

import json

from app.providers.base import LLMProvider
from app.schemas.requests import SummarizeReportRequest
from app.schemas.responses import ProviderMeta, ReportSummaryResponse
from app.services.analyzer import _run_with_runtime_fallback
from app.services.sanitizer import fence_user_content

_SYSTEM_PROMPT = """You are the management-summary writer for SmartWork AI's weekly \
Manager report. You are given a fixed set of already-computed metrics — status counts, \
completed/blocked/overdue/SLA-risk counts, department workload and parent ticket \
progress. You must:

1. Restate only what is in the metrics you were given. Never invent a number, a name, a \
task, or a cause that is not present in the input.
2. Never rank, score, compare, or single out individual employees. The input contains no \
employee names or per-employee figures; do not imply one exists.
3. Write `summary` as 2-4 plain sentences a Manager can read in ten seconds: what \
happened this week, and what needs attention.
4. Write `recommended_actions` as at most 4 short, concrete action items, each grounded \
directly in a metric you were given (e.g. "Review the N blocked tasks" — grounded in \
`blocked_count` — not "Improve team morale", which is not grounded in anything given).

Output only the requested JSON."""


async def summarize_report(
    request: SummarizeReportRequest, provider: LLMProvider
) -> tuple[ReportSummaryResponse, ProviderMeta]:
    user_content = fence_user_content(json.dumps(request.model_dump(), indent=2, default=str))

    def post_validate(_: ReportSummaryResponse) -> list[str]:
        return []

    parsed, meta, _warnings = await _run_with_runtime_fallback(
        provider,
        system_prompt=_SYSTEM_PROMPT,
        user_content=user_content,
        response_schema=ReportSummaryResponse,
        post_validate=post_validate,
        temperature=0.2,
        max_tokens=600,
    )
    return parsed, meta
