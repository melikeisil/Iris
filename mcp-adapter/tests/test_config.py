from app.config import Settings


def test_settings_expose_only_mcp_runtime_configuration() -> None:
    fields = set(Settings.model_fields)
    assert fields == {
        "smartwork_api_base_url",
        "mcp_http_port",
        "request_timeout_seconds",
        "log_level",
    }
    assert "database_url" not in fields
    assert not any(name.startswith("jira") for name in fields)
