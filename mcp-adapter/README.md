# SmartWork AI MCP Adapter

Privilege-free Model Context Protocol adapter for SmartWork AI.

## Purpose

This service exposes selected SmartWork AI capabilities to MCP hosts over
Streamable HTTP. It does not connect to PostgreSQL, it does not call Jira, and
it forwards the caller's own bearer token to the Spring Boot backend.

## Configuration

Environment variables:

- `SMARTWORK_API_BASE_URL` default `http://backend:8080/api/v1`
- `MCP_HTTP_PORT` default `9000`
- `REQUEST_TIMEOUT_SECONDS` default `10`
- `LOG_LEVEL` default `INFO`

## Run locally

```bash
cd mcp-adapter
python3 -m pip install -e ".[dev]"
uvicorn app.server:app --host 0.0.0.0 --port 9000
```

Health:

- `GET /health`

MCP endpoint:

- `POST /mcp`

## Supported resources

- `work-item://{workItemId}`
- `ticket://{ticketId}`
- `work-item-links://{workItemId}`
- `approval-queue://pending/{page}/{size}`

## Supported tools

- `get_work_item`
- `analyze_work_item`
- `list_child_work_items`
- `list_work_item_links`
- `get_pending_approvals`
- `get_team_lead_review_queue`
- `get_process_owner_review_queue`
- `get_manager_escalations`
- `route_work_item`

## Notes

- `route_work_item` deliberately mirrors the existing backend route endpoint.
  It may auto-assign or open an approval request exactly as the REST API does.
- The adapter adds no privileges. A backend `403` remains a `403`-equivalent MCP
  error.
