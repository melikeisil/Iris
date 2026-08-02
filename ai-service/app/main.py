"""FastAPI app, middleware, exception handlers — contract §1-§3, §8, §11.

Lifespan startup does two things that are both "fail loudly, fail early" choices mirrored
from the backend's `StartupSecretValidator`: refuse to start with no `SERVICE_TOKEN`, and
select the LLM provider once (contract §7's `auto` probe) rather than per-request.
"""

from __future__ import annotations

import logging
import time
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request, Response
from fastapi.responses import JSONResponse
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest
from pydantic import ValidationError

from app.api import analyze, decompose, health, report, skills
from app.config import settings
from app.observability.logging import configure_logging
from app.providers.factory import select_provider
from app.schemas.responses import AnalysisValidationError
from app.services.analyzer import AnalysisFailedError

configure_logging()
logger = logging.getLogger("smartwork.ai.main")

CORRELATION_ID_HEADER = "X-Correlation-Id"


@asynccontextmanager
async def lifespan(app: FastAPI):
    if not settings.service_token:
        raise RuntimeError(
            "SERVICE_TOKEN is not set. Refusing to start: an AI service with no shared "
            "secret would accept requests from anything on the network, not just the "
            "backend. Set SERVICE_TOKEN in the environment (see .env.example)."
        )
    app.state.provider = await select_provider()
    logger.info(
        "ai_service_started",
        extra={
            "provider": app.state.provider.name,
            "model": app.state.provider.model,
            "is_mock": app.state.provider.is_mock,
        },
    )
    yield


app = FastAPI(
    title="SmartWork AI — AI Analysis Service",
    version="0.1.0",
    lifespan=lifespan,
)

app.include_router(health.router)
app.include_router(analyze.router)
app.include_router(decompose.router)
app.include_router(skills.router)
app.include_router(report.router)


@app.middleware("http")
async def correlation_id_middleware(request: Request, call_next):
    correlation_id = request.headers.get(CORRELATION_ID_HEADER) or str(uuid.uuid4())
    start = time.perf_counter()
    response: Response = await call_next(request)
    response.headers[CORRELATION_ID_HEADER] = correlation_id
    logger.info(
        "http_request",
        extra={
            "correlation_id": correlation_id,
            "path": request.url.path,
            "method": request.method,
            "status_code": response.status_code,
            "duration_ms": int((time.perf_counter() - start) * 1000),
        },
    )
    return response


@app.exception_handler(AnalysisFailedError)
async def analysis_failed_handler(request: Request, exc: AnalysisFailedError) -> JSONResponse:
    body = AnalysisValidationError(
        errors=exc.errors,
        raw_output_hash=exc.raw_output_hash,
        attempts=exc.attempts,
        injection_suspected=exc.injection_suspected,
    )
    return JSONResponse(status_code=422, content=body.model_dump())


@app.exception_handler(ValidationError)
async def pydantic_validation_handler(request: Request, exc: ValidationError) -> JSONResponse:
    return JSONResponse(status_code=400, content={"errors": [str(e) for e in exc.errors()]})


@app.get("/metrics")
async def metrics() -> Response:
    return Response(content=generate_latest(), media_type=CONTENT_TYPE_LATEST)
