from __future__ import annotations

import contextlib
import json
from collections.abc import AsyncIterator

from mcp.server.fastmcp import FastMCP
from starlette.applications import Starlette
from starlette.middleware import Middleware
from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse
from starlette.routing import Mount, Route

from app.auth import reset_bearer_token, set_bearer_token
from app.client import SmartWorkApiClient
from app.config import Settings
from app.errors import BackendError, BackendUnavailable, raise_mcp_error, raise_unavailable

settings = Settings()
client = SmartWorkApiClient(settings)

mcp = FastMCP(
    "SmartWork AI",
    instructions="Privilege-free SmartWork AI adapter. Forwards the caller JWT to the backend.",
    stateless_http=True,
    json_response=True,
)


def _resource(summary: str, data: object) -> str:
    return json.dumps({"summary": summary, "data": data}, indent=2, ensure_ascii=False)


async def _get(path: str, *, params: dict[str, object] | None = None) -> object:
    try:
        return await client.get(path, params=params)
    except BackendError as exc:
        raise_mcp_error(exc)
    except BackendUnavailable as exc:
        raise_unavailable(exc)


async def _post(path: str, *, body: dict[str, object] | None = None) -> object:
    try:
        return await client.post(path, body=body)
    except BackendError as exc:
        raise_mcp_error(exc)
    except BackendUnavailable as exc:
        raise_unavailable(exc)


class AuthContextMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request: Request, call_next):
        token = set_bearer_token(request.headers.get("authorization"))
        try:
            return await call_next(request)
        finally:
            reset_bearer_token(token)


@mcp.resource("work-item://{work_item_id}")
async def work_item_resource(work_item_id: str) -> str:
    data = await _get(f"/work-items/{work_item_id}")
    return _resource("Work item detail with permissions and transitions.", data)


@mcp.resource("ticket://{ticket_id}")
async def ticket_resource(ticket_id: str) -> str:
    data = await _get(f"/work-items/{ticket_id}")
    if data.get("workItemType") not in {"REQUEST", "INCIDENT"}:
        raise RuntimeError("Not found or not visible to you.")
    return _resource("Ticket detail constrained to REQUEST or INCIDENT.", data)


@mcp.resource("work-item-links://{work_item_id}")
async def work_item_links_resource(work_item_id: str) -> str:
    data = await _get(f"/work-items/{work_item_id}/links")
    return _resource("Typed work item links rendered in both directions.", data)


@mcp.resource("approval-queue://pending/{page}/{size}")
async def approval_queue_resource(page: str, size: str) -> str:
    data = await _get("/approvals/pending", params={"page": int(page), "size": int(size)})
    return _resource("The caller's pending review queue.", data)


@mcp.tool()
async def get_work_item(work_item_id: str) -> object:
    """Fetch a SmartWork AI work item."""
    return await _get(f"/work-items/{work_item_id}")


@mcp.tool()
async def analyze_work_item(work_item_id: str) -> object:
    """Run AI analysis for a work item."""
    return await _post(f"/work-items/{work_item_id}/analyze")


@mcp.tool()
async def list_child_work_items(work_item_id: str) -> object:
    """List direct children and rolled-up progress for a work item."""
    return await _get(f"/work-items/{work_item_id}/children")


@mcp.tool()
async def list_work_item_links(work_item_id: str) -> object:
    """List typed links for a work item."""
    return await _get(f"/work-items/{work_item_id}/links")


@mcp.tool()
async def get_pending_approvals(page: int = 0, size: int = 20) -> object:
    """Fetch the caller's pending approval queue."""
    return await _get("/approvals/pending", params={"page": page, "size": size})


@mcp.tool()
async def get_team_lead_review_queue() -> object:
    """Fetch the team lead review queue for the caller."""
    return await _get("/team-lead/review-queue")


@mcp.tool()
async def get_process_owner_review_queue() -> object:
    """Fetch the process owner review queue for the caller."""
    return await _get("/process-owner/review-queue")


@mcp.tool()
async def get_manager_escalations() -> object:
    """Fetch manager escalations visible to the caller."""
    return await _get("/manager/escalations")


@mcp.tool()
async def route_work_item(work_item_id: str) -> object:
    """Run candidate ranking and policy routing for a work item."""
    return await _post(f"/work-items/{work_item_id}/route")


@mcp.prompt()
def analyze_ticket(ticket_id: str) -> str:
    return (
        "Open ticket://{ticket_id}, explain the current analysis state, "
        "missing information, priority, risk and next step without inventing data."
    ).format(ticket_id=ticket_id)


@mcp.prompt()
def explain_routing(work_item_id: str) -> str:
    return (
        "Call route_work_item for {work_item_id} only if the user has asked to apply routing. "
        "Otherwise inspect the work item and explain what queue or assignee it should reach."
    ).format(work_item_id=work_item_id)


@mcp.prompt()
def review_queue_summary(route: str = "pending") -> str:
    return (
        "Summarize the caller's {route} queue, list urgent items first, and never imply access "
        "to any item that the SmartWork AI backend did not return."
    ).format(route=route)


async def health(_: Request) -> JSONResponse:
    return JSONResponse({"status": "UP"})


@contextlib.asynccontextmanager
async def lifespan(_: Starlette) -> AsyncIterator[None]:
    async with contextlib.AsyncExitStack() as stack:
        await stack.enter_async_context(mcp.session_manager.run())
        yield


app = Starlette(
    routes=[
        Route("/health", health),
        Mount("/", app=mcp.streamable_http_app()),
    ],
    middleware=[Middleware(AuthContextMiddleware)],
    lifespan=lifespan,
)
