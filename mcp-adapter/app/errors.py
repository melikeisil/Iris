from dataclasses import dataclass
from typing import Any


@dataclass(slots=True)
class BackendError(Exception):
    status_code: int
    code: str | None = None
    detail: str | None = None
    payload: dict[str, Any] | None = None


class BackendUnavailable(Exception):
    pass


def _message_for(error: BackendError) -> str:
    if error.status_code == 401:
        return "Authentication failed. Reconnect SmartWork AI in your MCP settings."
    if error.status_code == 403:
        return "You do not have permission to do this in SmartWork AI."
    if error.status_code == 404:
        return "Not found or not visible to you."
    if error.status_code == 409:
        return "The item changed while you were working on it. Fetch it again."
    if error.status_code == 422 and error.code == "RECOMMENDATION_STALE":
        return "This recommendation is stale because the item changed in Jira. Regenerate it first."
    if error.status_code == 429:
        return "SmartWork AI rate-limited the request. Retry after a short delay."
    return "SmartWork AI is unavailable."


def raise_mcp_error(error: BackendError) -> None:
    raise RuntimeError(_message_for(error)) from error


def raise_unavailable(error: Exception) -> None:
    raise RuntimeError("SmartWork AI is unavailable.") from error
