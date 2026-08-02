"""POST /internal/v1/decompose — contract §3."""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends, Request

from app.deps import require_service_token
from app.observability.metrics import (
    analysis_latency_seconds,
    analysis_requests_total,
    analysis_validation_failures_total,
)
from app.schemas.requests import DecomposeRequest
from app.schemas.responses import DecomposeEnvelope
from app.services import decomposer
from app.services.analyzer import AnalysisFailedError

router = APIRouter(prefix="/internal/v1", tags=["decompose"], dependencies=[Depends(require_service_token)])
logger = logging.getLogger("smartwork.ai.api")


@router.post("/decompose", response_model=DecomposeEnvelope)
async def decompose(body: DecomposeRequest, request: Request) -> DecomposeEnvelope:
    provider = request.app.state.provider
    start = time.perf_counter()
    try:
        envelope = await decomposer.decompose(body, provider)
    except AnalysisFailedError:
        analysis_validation_failures_total.labels(stage="repair_exhausted").inc()
        raise
    analysis_requests_total.labels(endpoint="decompose", provider=provider.name).inc()
    analysis_latency_seconds.labels(endpoint="decompose", provider=provider.name).observe(
        time.perf_counter() - start
    )
    logger.info(
        "decomposition_completed",
        extra={
            "correlation_id": body.correlation_id,
            "work_item_id": body.work_item_id,
            "endpoint": "decompose",
            "provider": envelope.meta.provider,
            "item_count": len(envelope.decomposition.suggested_work_items),
            "attempts": envelope.meta.attempts,
            "warnings": envelope.decomposition.warnings,
        },
    )
    return envelope
