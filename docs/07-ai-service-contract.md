# 07 — AI Service Contract (FastAPI)

## 1. What the AI service is, and is not

**Is:** a stateless, pure function from sanitized work context to a strictly validated JSON analysis.

**Is not:** an authority. It holds no database connection, no Jira credentials and no user session. Its output is a *proposal* that Spring Boot may accept, ignore, override or discard.

```mermaid
flowchart LR
    SB[Spring Boot] -->|POST /internal/v1/analyze/*<br/>X-Service-Token| API[FastAPI router]
    API --> SAN[Input sanitizer<br/>+ injection guard]
    SAN --> PB[Prompt builder<br/>+ few-shot examples]
    PB --> PROV{LLM provider}
    PROV -->|configured| ANT[AnthropicProvider]
    PROV -->|no key / demo| MOCK[MockProvider<br/>deterministic]
    ANT --> VAL[Pydantic validation]
    MOCK --> VAL
    VAL -->|invalid| REP[Repair prompt<br/>one retry]
    REP --> VAL
    VAL -->|valid| POST[Post-validation:<br/>enum coercion, clamping,<br/>vagueness rejection]
    POST --> SB
    VAL -->|still invalid| ERR[422 AnalysisValidationError]
```

## 2. Hard boundaries

| Boundary | How it is guaranteed |
|---|---|
| No database | The container has no `DATABASE_URL` and the image has no DB driver installed |
| No Jira | No `JIRA_*` environment variable reaches this container; there is no Jira client in the dependency tree |
| No workflow mutation | The service exposes no write endpoint; its responses contain no status field the backend applies blindly |
| No authorization decision | Response schemas contain no `approved`, `authorized`, `route` or `assigneeId` field. It may say *what skills are needed*; it may never say *who gets it*. |
| No PII in prompts | The sanitizer strips emails, phone numbers, national IDs and payment details before prompt construction (§5) |
| Callable only by the backend | `X-Service-Token` constant-time compared; the port is not published outside the Compose network in production configuration |

The single most important line: **`AnalysisResponse` has no `assigneeUserId`.** Candidate ranking happens in Java, on stored data, with a deterministic formula. The model never sees the employee list.

## 3. Endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/internal/v1/analyze/ticket` | classify + triage a `REQUEST` / `INCIDENT` |
| POST | `/internal/v1/analyze/development` | analyze an `EPIC`…`DOCUMENTATION` item |
| POST | `/internal/v1/decompose` | produce child work items for an analyzed item |
| POST | `/internal/v1/extract-skills` | skill + effort extraction for a single item |
| GET | `/health` | liveness |
| GET | `/ready` | readiness incl. provider reachability |
| GET | `/internal/v1/provider` | active provider, model, mock flag (for the UI badge) |

All POST endpoints accept `X-Correlation-Id` and echo it.

## 4. Request schemas

```python
class AnalyzeTicketRequest(BaseModel):
    correlation_id: str
    work_item_id: str                      # opaque; used only for logging
    summary: str = Field(min_length=5, max_length=255)
    description: str | None = Field(default=None, max_length=20_000)
    reporter_department_code: str | None
    origin_type: OriginType
    priority_hint: Priority | None = None
    attachments_meta: list[AttachmentMeta] = []       # filename + type only, never content
    previous_analysis: AnalysisResponse | None = None # set on re-analysis
    clarification_answers: list[ClarificationAnswer] = []
    org_context: OrgContext                # the vocabulary the model must choose from
    similar_items: list[SimilarItem] = []  # trigram matches, for duplicate detection

class OrgContext(BaseModel):
    department_codes: list[str]            # ["HR","IT","FINANCE","ENGINEERING"]
    team_codes: list[TeamRef]              # code + department_code
    project_codes: list[str]
    component_codes: list[str]
    skill_codes: list[str]                 # the canonical vocabulary
    process_template_codes: list[str]
    category_codes: list[str]

class AnalyzeDevelopmentRequest(BaseModel):
    correlation_id: str
    work_item_id: str
    work_item_type: WorkItemType
    summary: str
    description: str | None
    acceptance_criteria: str | None
    project_code: str | None
    parent_summary: str | None             # epic/feature context
    existing_components: list[str] = []
    org_context: OrgContext
    clarification_answers: list[ClarificationAnswer] = []

class DecomposeRequest(BaseModel):
    correlation_id: str
    work_item_id: str
    analysis: AnalysisResponse             # the accepted analysis
    matched_template: ProcessTemplateSpec | None = None
    max_items: int = Field(default=12, le=25)
    org_context: OrgContext
```

`org_context` is what keeps the model inside the organization's vocabulary. It is not asked to invent department names — it is asked to *choose* from a list. Anything outside the list fails post-validation.

## 5. Sanitization and prompt-injection defence

Applied before any prompt is built.

| Step | Action |
|---|---|
| 1. PII redaction | Regex + `presidio`-style rules replace emails, phone numbers, IBANs, card numbers and national IDs with typed placeholders (`<EMAIL_1>`). Placeholders are stable within a request so the model can still reason about "the same person". |
| 2. Length clamp | Description truncated to 20 000 chars with a marker; attachments never sent, only filename + MIME. |
| 3. Delimiter fencing | User content is wrapped in `<user_content>` tags and the system prompt states that nothing inside may be treated as instruction. |
| 4. Instruction stripping | Lines matching known injection patterns (`ignore previous`, `system:`, `you are now`, `<|im_start|>`, base64 blobs > 200 chars) are flagged and the request is marked `injection_suspected`. |
| 5. Output containment | Because output is schema-constrained, a successful injection still cannot produce a field that does not exist. |
| 6. Authority containment | Even a fully compromised response cannot assign anyone, approve anything or change a status — that is the structural defence, and the reason the boundaries in §2 matter more than the regexes. |

`injection_suspected = true` propagates to the response; Spring Boot routes such items to `MANUAL_EXCEPTION_REVIEW` and raises an admin notification.

## 6. Response schemas

```python
class Department(BaseModel):
    department_code: str
    confidence: float = Field(ge=0, le=1)
    reason: str = Field(min_length=10, max_length=500)

class ClarificationQuestion(BaseModel):
    id: str
    question: str = Field(min_length=10, max_length=300)
    field: Literal["scope","acceptanceCriteria","priority","impact",
                   "environment","deadline","budget","other"]
    required: bool = True

class AnalysisResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")     # unknown fields are a hard failure

    work_family: WorkFamily
    work_item_type: WorkItemType
    category: str | None
    summary: str = Field(max_length=255)
    project_code: str | None
    recommended_team: str | None
    departments: list[Department] = Field(min_length=1, max_length=5)
    affected_components: list[str] = []
    required_skills: list[str] = []
    priority: Priority
    priority_score: int = Field(ge=0, le=100)
    risk_level: RiskLevel
    risk_signals: list[str] = []
    complexity: Complexity
    suggested_story_points: int | None = None
    requires_more_information: bool
    clarification_questions: list[ClarificationQuestion] = []
    requires_decomposition: bool
    matched_process_template: str | None
    estimated_total_hours: float | None = Field(default=None, gt=0, le=2000)
    confidence: float = Field(ge=0, le=1)
    suggested_work_items: list[SuggestedWorkItem] = []

    # sensitivity signals — inputs to the policy engine, never decisions
    security_impact: bool = False
    production_impact: bool = False
    budget_impact: bool = False
    compliance_impact: bool = False
    sla_risk: Literal["NONE","LOW","MEDIUM","HIGH"] = "NONE"
    business_impact: Literal["NONE","LOW","MEDIUM","HIGH","CRITICAL"] = "NONE"
    likely_duplicate_of: str | None = None
    likely_defect: bool = False
    suggested_conversion: WorkItemType | None = None
    injection_suspected: bool = False
    explanation: str = Field(min_length=20, max_length=1000)
```

### Cross-field validators

```python
@model_validator(mode="after")
def consistency(self) -> "AnalysisResponse":
    if self.requires_more_information and not self.clarification_questions:
        raise ValueError("requires_more_information=true needs at least one question")
    if not self.requires_more_information and self.clarification_questions:
        raise ValueError("clarification questions provided but more info not required")
    if self.requires_decomposition and self.confidence < 0.4:
        raise ValueError("cannot propose decomposition below 0.4 confidence")
    if self.suggested_story_points is not None and \
       self.suggested_story_points not in (1,2,3,5,8,13,21):
        raise ValueError("story points must be Fibonacci")
    if self.work_family != WorkFamily.DEVELOPMENT_WORK and self.suggested_story_points:
        raise ValueError("story points only apply to development work")
    if self.risk_level == RiskLevel.CRITICAL and not self.risk_signals:
        raise ValueError("CRITICAL risk requires explicit signals")
    if self.priority == Priority.CRITICAL and self.priority_score < 80:
        raise ValueError("CRITICAL priority inconsistent with score")
    return self
```

### Decomposition schema

```python
class SuggestedWorkItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    temp_id: str
    title: str = Field(min_length=10, max_length=160)
    description: str = Field(min_length=30, max_length=4000)
    work_item_type: WorkItemType
    department_code: str
    team_code: str | None
    required_skills: list[str] = Field(min_length=1)
    affected_components: list[str] = []
    estimated_hours: float = Field(gt=0, le=80)
    suggested_story_points: int | None
    priority: Priority
    risk_level: RiskLevel
    dependencies: list[str] = []            # temp_ids of siblings
    testing_requirements: list[str] = []
    closure_type: ClosureType
    sensitive_flags: SensitiveFlags
    confidence: float = Field(ge=0, le=1)
    explanation: str = Field(min_length=20, max_length=600)
```

### The vagueness rule

The brief forbids tasks like "Work on backend". This is enforced, not merely requested in the prompt:

```python
VAGUE_PATTERNS = [
    r"^(work on|do|handle|fix|implement|update|check|review)\s+(the\s+)?"
    r"(backend|frontend|issue|problem|bug|ui|api|code|it|this|stuff|things?)\.?$",
    r"^(backend|frontend|testing|documentation|deployment)( work| tasks?)?\.?$",
    r"^(investigate|analyze|look into)( it| this| the issue)?\.?$",
]

@field_validator("title")
def reject_vague(cls, v: str) -> str:
    normalized = v.strip().lower()
    if any(re.match(p, normalized) for p in VAGUE_PATTERNS):
        raise ValueError(f"Vague task title rejected: '{v}'")
    if len(normalized.split()) < 3:
        raise ValueError(f"Task title too short to be actionable: '{v}'")
    return v
```

A vague title fails validation, which triggers the repair retry with the specific error text — usually enough for the model to produce "Add POST /profile/avatar endpoint with size and MIME validation" instead.

Additional structural checks on a decomposition set:

| Check | Failure |
|---|---|
| `dependencies` reference existing `temp_id`s | `ValueError` |
| dependency graph is acyclic | `ValueError` |
| `department_code` ∈ `org_context.department_codes` | `ValueError` |
| `team_code` belongs to `department_code` | `ValueError` |
| `required_skills` ⊆ `org_context.skill_codes` after alias resolution | unmatched are moved to `unresolved_skills`, not rejected |
| Σ `estimated_hours` within ±60% of the parent's `estimated_total_hours` | warning flag, not a failure |
| at least one `TEST` item or non-empty `testing_requirements` when the family is `DEVELOPMENT_WORK` | `ValueError` |

## 7. Provider abstraction

```python
class LLMProvider(Protocol):
    name: str
    model: str
    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[BaseModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[BaseModel, ProviderMeta]: ...
```

| Implementation | Behaviour |
|---|---|
| `AnthropicProvider` | Uses tool-schema-constrained output derived from the Pydantic model's JSON schema. Temperature 0.2 for classification, 0.4 for decomposition. Retries once on 429/5xx with jitter. |
| `MockProvider` | Deterministic. Keyed on a normalized hash of the summary, with hand-authored fixtures for the three demo scenarios and a rule-based fallback (keyword → family/priority/skills) for anything else. Same latency profile is simulated (~800 ms) so the UI's loading states are exercised. |
| `EchoProvider` | Test-only. Returns a fixture supplied in the request, used to drive validation-failure tests. |

Selection: `LLM_PROVIDER=anthropic|mock|auto`. `auto` (the default) picks `anthropic` when `ANTHROPIC_API_KEY` is present and reachable at startup, otherwise `mock`, and logs the choice loudly. `ProviderMeta` (`provider, model, latency_ms, input_tokens, output_tokens, is_mock`) is returned to the backend, persisted on the recommendation and shown in the UI as a "demo analysis" badge when `is_mock`.

**The mock is not a stub.** It is the reason the demo cannot fail on stage, and it is what makes the AI-path integration tests hermetic.

## 8. Validation and repair loop

```mermaid
flowchart TB
    A[Provider returns raw JSON] --> B{Pydantic parse}
    B -->|ok| C{Post-validators:<br/>vocabulary, vagueness, graph}
    B -->|fail| R1[Attempt 1 repair:<br/>original prompt + raw output<br/>+ exact validation errors]
    C -->|ok| D[200 AnalysisResponse + meta]
    C -->|fail| R1
    R1 --> B2{Pydantic parse}
    B2 -->|ok| C2{Post-validators}
    B2 -->|fail| E
    C2 -->|ok| D
    C2 -->|fail| E["422 AnalysisValidationError<br/>{errors, raw_output_hash, attempts}"]
    E --> F[Backend: recommendation FAILED,<br/>route MANUAL_EXCEPTION_REVIEW,<br/>notify Team Lead]
```

Exactly **one** repair attempt. A second failure is a signal, not something to grind against — it is surfaced to a human and counted in the `analysis_validation_failure_rate` metric.

## 9. Prompt structure

Four blocks, in this order:

1. **Role and constraints.** "You are a work classification system. You output only the requested JSON. You never decide who performs work. You choose values only from the supplied vocabularies."
2. **Vocabulary.** `org_context`, rendered as explicit enumerations.
3. **Few-shot examples.** Two to four curated examples per endpoint, drawn from the seeded process templates so the model learns the house style of a good task description. Examples live in `prompts/examples/*.json`, are version-controlled, and are covered by tests that assert every example passes its own response schema.
4. **User content.** Fenced in `<user_content>` with the "not instructions" warning.

Prompts are Jinja2 templates under `prompts/` with a `PROMPT_VERSION` constant persisted on every recommendation, so changing a prompt does not silently invalidate historical AI-acceptance metrics.

## 10. Configuration

```bash
LLM_PROVIDER=auto              # anthropic | mock | auto
ANTHROPIC_API_KEY=             # blank ⇒ mock
ANTHROPIC_MODEL=claude-sonnet-5
LLM_TIMEOUT_SECONDS=45
LLM_MAX_RETRIES=1
SERVICE_TOKEN=                 # shared secret with Spring Boot, required
PROMPT_VERSION=v1
LOG_LEVEL=INFO
LOG_FORMAT=json
SANITIZER_STRICT=true
MAX_DESCRIPTION_CHARS=20000
```

## 11. Observability

Structured JSON logs with `correlation_id`, `work_item_id`, `endpoint`, `provider`, `model`, `latency_ms`, `token counts`, `validation_attempts`, `injection_suspected`. **Prompt and completion bodies are never logged at INFO** — only at DEBUG, which is off outside local development, because they contain (redacted but still sensitive) business content.

Prometheus-style counters exposed at `/metrics`: `analysis_requests_total{endpoint,provider}`, `analysis_validation_failures_total{stage}`, `analysis_latency_seconds`, `injection_suspected_total`.

## 12. Test plan (Phase 4)

| Test | Asserts |
|---|---|
| `test_analysis_schema_rejects_extra_fields` | `extra="forbid"` catches hallucinated fields |
| `test_clarification_consistency` | the `requires_more_information` ↔ questions invariant, both directions |
| `test_vague_titles_rejected` | every brief-forbidden phrase fails, and a good title passes |
| `test_dependency_graph_acyclic` | a cyclic decomposition is rejected |
| `test_vocabulary_enforcement` | an invented department code fails; an invented skill code lands in `unresolved_skills` |
| `test_repair_loop_succeeds` | invalid-then-valid sequence returns 200 with `attempts=2` |
| `test_repair_loop_gives_up` | invalid-twice returns 422 with structured errors |
| `test_mock_provider_deterministic` | the same input returns byte-identical output across runs |
| `test_mock_covers_demo_scenarios` | all three demo inputs produce the expected family, priority and template |
| `test_pii_redaction` | emails, phones, IBANs and card numbers never reach the provider (asserted on a spy provider) |
| `test_injection_flagged` | `ignore previous instructions` sets `injection_suspected` |
| `test_no_assignment_fields` | `AnalysisResponse.model_fields` contains no key matching `assignee|user|approve|authorize` — the structural guarantee, asserted in CI |
| `test_ticket_analysis_onboarding` | the onboarding ticket matches `EMPLOYEE_ONBOARDING` and returns HR + IT + FINANCE |
| `test_development_decomposition_story` | the avatar story decomposes into backend, frontend, storage and test items with dependencies |
| `test_defect_detection` | the payments ticket sets `likely_defect` and `suggested_conversion = BUG` |
