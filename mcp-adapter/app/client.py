import json
import uuid
from collections.abc import Mapping
from typing import Any

import httpx

from app.auth import current_bearer_token
from app.config import Settings
from app.errors import BackendError, BackendUnavailable


class SmartWorkApiClient:
    def __init__(
        self,
        settings: Settings,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._settings = settings
        self._transport = transport

    async def get(self, path: str, params: Mapping[str, Any] | None = None) -> Any:
        return await self._request("GET", path, params=params)

    async def post(self, path: str, body: Mapping[str, Any] | None = None) -> Any:
        return await self._request("POST", path, json=body)

    async def _request(self, method: str, path: str, **kwargs: Any) -> Any:
        token = current_bearer_token()
        if not token:
            raise BackendError(401, detail="Missing bearer token")

        headers = kwargs.pop("headers", {})
        headers["Authorization"] = token
        headers["X-Client"] = "mcp-adapter"
        headers["X-Correlation-Id"] = str(uuid.uuid4())

        try:
            async with httpx.AsyncClient(
                base_url=self._settings.normalized_base_url,
                timeout=self._settings.request_timeout_seconds,
                transport=self._transport,
            ) as client:
                response = await client.request(method, path, headers=headers, **kwargs)
        except httpx.TimeoutException as exc:
            raise BackendUnavailable() from exc
        except httpx.HTTPError as exc:
            raise BackendUnavailable() from exc

        if response.is_error:
            raise self._to_backend_error(response)

        if not response.content:
            return None
        return response.json()

    @staticmethod
    def _to_backend_error(response: httpx.Response) -> BackendError:
        payload: dict[str, Any] = {}
        try:
            payload = response.json()
        except json.JSONDecodeError:
            payload = {}
        return BackendError(
            status_code=response.status_code,
            code=payload.get("code"),
            detail=payload.get("detail"),
            payload=payload,
        )
