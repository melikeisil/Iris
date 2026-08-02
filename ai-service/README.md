# SmartWork AI — AI Analysis Service (Phase 4)

A stateless FastAPI service that classifies, triages and decomposes work. It holds no
database connection, no Jira credentials and no user session — see
[docs/07-ai-service-contract.md](../docs/07-ai-service-contract.md) for the full contract
this implementation follows.

## Running

Via Docker Compose (recommended — this is how it runs in the full system):

```bash
cp ../.env.example ../.env   # from repo root, if not already done
docker compose up --build ai-service
```

Standalone, for local development:

```bash
cd ai-service
cp .env.example .env
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8000
```

With no provider key set, the service selects the deterministic `MockProvider` at startup
and logs that choice loudly. `LLM_PROVIDER=auto` prefers Gemini, then Anthropic, when a
configured key is reachable. **Mock is still the default demo path** — the three demo
scenarios in `app/providers/mock_provider.py` are hand-authored fixtures, not stubs.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/internal/v1/analyze/ticket` | classify + triage a REQUEST / INCIDENT |
| POST | `/internal/v1/analyze/development` | analyze an EPIC…DOCUMENTATION item |
| POST | `/internal/v1/decompose` | produce child work items for an analyzed item |
| POST | `/internal/v1/extract-skills` | skill + effort extraction for a single item |
| GET | `/health` | liveness |
| GET | `/ready` | readiness, including which provider is active |
| GET | `/internal/v1/provider` | active provider/model/mock flag, for the UI badge |
| GET | `/metrics` | Prometheus counters |

Every POST endpoint requires `X-Service-Token` (constant-time compared against
`SERVICE_TOKEN`) and accepts/echoes `X-Correlation-Id`.

## Example request

```bash
curl -s http://localhost:8000/internal/v1/analyze/ticket \
  -H "X-Service-Token: dev-only-service-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "correlation_id": "demo-1",
    "work_item_id": "wi-1",
    "summary": "Prepare all onboarding activities for a new employee starting Monday.",
    "origin_type": "USER_CREATED",
    "org_context": {
      "department_codes": ["HR", "IT", "FINANCE", "ENGINEERING"],
      "team_codes": [{"code": "HR_OPS", "department_code": "HR"}],
      "project_codes": [],
      "component_codes": [],
      "skill_codes": ["HR_ONBOARDING", "USER_PROVISIONING"],
      "process_template_codes": ["EMPLOYEE_ONBOARDING"],
      "category_codes": ["hr_onboarding"]
    }
  }' | python3 -m json.tool
```

Expect `work_family: PROCESS_WORK`, `priority: HIGH`, `requires_more_information: true`,
and two clarification questions (role, start date) — see Demo 1 in
[docs/14-scope-and-roadmap.md](../docs/14-scope-and-roadmap.md).

## Tests

```bash
pip install -e ".[dev]"
pytest
ruff check app tests
mypy app
```

| File | Covers |
|---|---|
| `test_schemas.py` | `extra="forbid"`, clarification/priority/risk/story-point consistency, every few-shot example validates against its own schema |
| `test_validators.py` | vague-title rejection, acyclic dependency graph, department/team vocabulary enforcement, mandatory testing coverage on development work |
| `test_sanitizer.py` | PII redaction (email/IBAN/card/phone), stable placeholders, injection-pattern flagging, length clamp |
| `test_repair_loop.py` | one repair attempt on invalid JSON or a schema violation; gives up cleanly after the second failure |
| `test_mock_provider.py` | determinism, all three demo scenarios (with and without clarification answers), the keyword-based fallback |
| `test_no_authorization_fields.py` | the structural guarantee: no response schema anywhere in this service can name an assignee, an approval, or a status |

## Known limitations (Phase 4)

- **Not executed in this environment.** The sandbox used to build this has Python 3.10
  with no network access to PyPI, so FastAPI/Pydantic v2/etc. could not be installed and
  `pytest` was never actually run here. Every file was verified with `python3 -m
  py_compile` (syntax only) and reviewed by hand against the contract. This mirrors the
  Java/Maven limitation noted for the backend in Phases 2–3 and should be treated the
  same way: run `pytest` yourself before trusting this beyond a design review.
- The real providers are untested against live API keys in this environment.
- The backend does not yet call this service — that wiring (an `AiServiceClient` in a new
  `analysis` module, with a circuit breaker per §7's boundary discussion) is Phase 5/6
  work, not Phase 4.
