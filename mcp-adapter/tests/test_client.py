import httpx
import pytest

from app.auth import reset_bearer_token, set_bearer_token
from app.client import SmartWorkApiClient
from app.config import Settings
from app.errors import BackendError


@pytest.mark.asyncio
async def test_client_forwards_caller_jwt_and_mcp_headers() -> None:
    seen: dict[str, str] = {}

    async def handler(request: httpx.Request) -> httpx.Response:
        seen["authorization"] = request.headers["Authorization"]
        seen["x-client"] = request.headers["X-Client"]
        seen["x-correlation-id"] = request.headers["X-Correlation-Id"]
        return httpx.Response(200, json={"ok": True})

    transport = httpx.MockTransport(handler)
    client = SmartWorkApiClient(Settings(), transport=transport)
    token = set_bearer_token("Bearer demo-token")
    try:
        payload = await client.get("/work-items/123")
    finally:
        reset_bearer_token(token)

    assert payload == {"ok": True}
    assert seen["authorization"] == "Bearer demo-token"
    assert seen["x-client"] == "mcp-adapter"
    assert seen["x-correlation-id"]


@pytest.mark.asyncio
async def test_client_maps_problem_json_to_backend_error() -> None:
    async def handler(_: httpx.Request) -> httpx.Response:
        return httpx.Response(
            403,
            json={
                "code": "ROUTE_NOT_PERMITTED",
                "detail": "Forbidden",
            },
        )

    transport = httpx.MockTransport(handler)
    client = SmartWorkApiClient(Settings(), transport=transport)
    token = set_bearer_token("Bearer demo-token")
    try:
        with pytest.raises(BackendError) as error:
            await client.get("/manager/escalations")
    finally:
        reset_bearer_token(token)

    assert error.value.status_code == 403
    assert error.value.code == "ROUTE_NOT_PERMITTED"
