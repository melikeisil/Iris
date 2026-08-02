"""POST /internal/v1/extract-skills — contract §3."""

from __future__ import annotations

import logging
import time

from fastapi import APIRouter, Depends, Request

from app.deps import require_service_token
from app.observability.metrics import analysis_latency_seconds, analysis_requests_total
from app.schemas.requests import ExtractSkillsRequest
from app.schemas.responses import ExtractSkillsEnvelope
from app.services import analyzer

router = APIRouter(prefix="/internal/v1", tags=["skills"], dependencies=[Depends(require_service_token)])
logger = logging.getLogger("smartwork.ai.api")


@router.post("/extract-skills", response_model=ExtractSkillsEnvelope)
async def extract_skills(body: ExtractSkillsRequest, request: Request) -> ExtractSkillsEnvelope:
    provider = request.app.state.provider
    start = time.perf_counter()
    envelope = await analyzer.extract_skills(body, provider)
    analysis_requests_total.labels(endpoint="extract_skills", provider=provider.name).inc()
    analysis_latency_seconds.labels(endpoint="extract_skills", provider=provider.name).observe(
        time.perf_counter() - start
    )
    logger.info(
        "skill_extraction_completed",
        extra={
            "correlation_id": body.correlation_id,
            "work_item_id": body.work_item_id,
            "endpoint": "extract_skills",
            "provider": envelope.meta.provider,
            "skill_count": len(envelope.extraction.skills),
        },
    )
    return envelope
