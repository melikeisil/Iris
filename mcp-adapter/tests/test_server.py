from starlette.testclient import TestClient

from app.errors import BackendError, raise_mcp_error
from app.server import app


def test_health_endpoint_is_up() -> None:
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_forbidden_error_translation_leaks_no_backend_detail() -> None:
    try:
        raise_mcp_error(BackendError(status_code=403, detail="Managers only"))
    except RuntimeError as error:
        assert str(error) == "You do not have permission to do this in SmartWork AI."
    else:
        raise AssertionError("Expected RuntimeError to be raised")
