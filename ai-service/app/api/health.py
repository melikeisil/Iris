"""GET /health, /ready, /internal/v1/provider — contract §3."""

from __future__ import annotations

from fastapi import APIRouter, Request

from app.config import settings

router = APIRouter(tags=["health"])


@router.get("/health")
async def health() -> dict:
    """Liveness only — no dependency checks. A slow/unreachable LLM provider should not
    take the container out of the load balancer's rotation."""
    return {"status": "UP"}


@router.get("/ready")
async def ready(request: Request) -> dict:
    """Readiness including provider reachability, so an orchestrator can tell a booting
    container from one that is up but has no usable provider selected."""
    provider = getattr(request.app.state, "provider", None)
    return {
        "status": "READY" if provider is not None else "NOT_READY",
        "provider": getattr(provider, "name", None),
        "model": getattr(provider, "model", None),
        "is_mock": getattr(provider, "is_mock", None),
    }


@router.get("/internal/v1/provider")
async def provider_info(request: Request) -> dict:
    """Feeds the UI's 'demo analysis' badge (contract §7)."""
    provider = request.app.state.provider
    return {
        "provider": provider.name,
        "model": provider.model,
        "is_mock": provider.is_mock,
        "prompt_version": settings.prompt_version,
    }
