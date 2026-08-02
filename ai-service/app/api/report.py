"""POST /internal/v1/summarize-report — the Manager weekly report's AI summary."""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends, Request

from app.deps import require_service_token
from app.observability.metrics import analysis_latency_seconds, analysis_requests_total
from app.schemas.requests import SummarizeReportRequest
from app.schemas.responses import ReportSummaryEnvelope
from app.services.report_summarizer import summarize_report

router = APIRouter(prefix="/internal/v1", tags=["report"], dependencies=[Depends(require_service_token)])
logger = logging.getLogger("smartwork.ai.api")


@router.post("/summarize-report", response_model=ReportSummaryEnvelope)
async def summarize(body: SummarizeReportRequest, request: Request) -> ReportSummaryEnvelope:
    provider = request.app.state.provider
    start = time.perf_counter()
    parsed, meta = await summarize_report(body, provider)
    analysis_requests_total.labels(endpoint="summarize_report", provider=meta.provider).inc()
    analysis_latency_seconds.labels(endpoint="summarize_report", provider=meta.provider).observe(
        time.perf_counter() - start
    )
    logger.info(
        "report_summary_completed",
        extra={
            "endpoint": "summarize_report",
            "provider": meta.provider,
            "period_start": body.period_start,
            "period_end": body.period_end,
        },
    )
    return ReportSummaryEnvelope(
        summary=parsed.summary, recommended_actions=parsed.recommended_actions, meta=meta
    )
