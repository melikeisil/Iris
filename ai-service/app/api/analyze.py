"""POST /internal/v1/analyze/ticket, /internal/v1/analyze/development — contract §3."""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends, Header, Request

from app.deps import require_service_token
from app.observability.metrics import (
    analysis_latency_seconds,
    analysis_requests_total,
    analysis_validation_failures_total,
    injection_suspected_total,
)
from app.schemas.requests import AnalyzeDevelopmentRequest, AnalyzeTicketRequest
from app.schemas.responses import AnalysisEnvelope
from app.services import analyzer
from app.services.analyzer import AnalysisFailedError

router = APIRouter(prefix="/internal/v1/analyze", tags=["analyze"], dependencies=[Depends(require_service_token)])
logger = logging.getLogger("smartwork.ai.api")


@router.post("/ticket", response_model=AnalysisEnvelope)
async def analyze_ticket(
    body: AnalyzeTicketRequest,
    request: Request,
    x_correlation_id: str | None = Header(default=None),
) -> AnalysisEnvelope:
    provider = request.app.state.provider
    start = time.perf_counter()
    try:
        envelope = await analyzer.analyze_ticket(body, provider)
    except AnalysisFailedError:
        analysis_validation_failures_total.labels(stage="repair_exhausted").inc()
        raise
    analysis_requests_total.labels(endpoint="analyze_ticket", provider=provider.name).inc()
    analysis_latency_seconds.labels(endpoint="analyze_ticket", provider=provider.name).observe(
        time.perf_counter() - start
    )
    if envelope.analysis.injection_suspected:
        injection_suspected_total.labels(endpoint="analyze_ticket").inc()
    logger.info(
        "analysis_completed",
        extra={
            "correlation_id": body.correlation_id,
            "work_item_id": body.work_item_id,
            "endpoint": "analyze_ticket",
            "provider": envelope.meta.provider,
            "model": envelope.meta.model,
            "latency_ms": envelope.meta.latency_ms,
            "attempts": envelope.meta.attempts,
            "injection_suspected": envelope.analysis.injection_suspected,
        },
    )
    return envelope


@router.post("/development", response_model=AnalysisEnvelope)
async def analyze_development(
    body: AnalyzeDevelopmentRequest,
    request: Request,
    x_correlation_id: str | None = Header(default=None),
) -> AnalysisEnvelope:
    provider = request.app.state.provider
    start = time.perf_counter()
    try:
        envelope = await analyzer.analyze_development(body, provider)
    except AnalysisFailedError:
        analysis_validation_failures_total.labels(stage="repair_exhausted").inc()
        raise
    analysis_requests_total.labels(endpoint="analyze_development", provider=provider.name).inc()
    analysis_latency_seconds.labels(endpoint="analyze_development", provider=provider.name).observe(
        time.perf_counter() - start
    )
    if envelope.analysis.injection_suspected:
        injection_suspected_total.labels(endpoint="analyze_development").inc()
    logger.info(
        "analysis_completed",
        extra={
            "correlation_id": body.correlation_id,
            "work_item_id": body.work_item_id,
            "endpoint": "analyze_development",
            "provider": envelope.meta.provider,
            "model": envelope.meta.model,
            "latency_ms": envelope.meta.latency_ms,
            "attempts": envelope.meta.attempts,
            "injection_suspected": envelope.analysis.injection_suspected,
        },
    )
    return envelope
