"""Request schemas — see docs/07-ai-service-contract.md §4."""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.enums import ClarificationField, OriginType, Priority, WorkItemType


class TeamRef(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    department_code: str


class OrgContext(BaseModel):
    """The vocabulary the model must choose from. Never invented, only selected."""

    model_config = ConfigDict(extra="forbid")

    department_codes: list[str] = Field(default_factory=list)
    team_codes: list[TeamRef] = Field(default_factory=list)
    project_codes: list[str] = Field(default_factory=list)
    component_codes: list[str] = Field(default_factory=list)
    skill_codes: list[str] = Field(default_factory=list)
    process_template_codes: list[str] = Field(default_factory=list)
    category_codes: list[str] = Field(default_factory=list)


class AttachmentMeta(BaseModel):
    model_config = ConfigDict(extra="forbid")

    filename: str
    content_type: str | None = None


class ClarificationAnswer(BaseModel):
    model_config = ConfigDict(extra="forbid")

    question_id: str
    field: ClarificationField
    answer: str = Field(min_length=1, max_length=2000)


class SimilarItem(BaseModel):
    """A trigram match surfaced for duplicate detection. Read-only context for the model."""

    model_config = ConfigDict(extra="forbid")

    reference_key: str
    summary: str
    similarity: float = Field(ge=0, le=1)


class AnalyzeTicketRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    correlation_id: str
    work_item_id: str
    summary: str = Field(min_length=5, max_length=255)
    description: str | None = Field(default=None, max_length=20_000)
    reporter_department_code: str | None = None
    origin_type: OriginType
    priority_hint: Priority | None = None
    attachments_meta: list[AttachmentMeta] = Field(default_factory=list)
    previous_analysis: "AnalysisResponse | None" = None
    clarification_answers: list[ClarificationAnswer] = Field(default_factory=list)
    org_context: OrgContext
    similar_items: list[SimilarItem] = Field(default_factory=list)


class AnalyzeDevelopmentRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    correlation_id: str
    work_item_id: str
    work_item_type: WorkItemType
    summary: str = Field(min_length=5, max_length=255)
    description: str | None = Field(default=None, max_length=20_000)
    acceptance_criteria: str | None = Field(default=None, max_length=10_000)
    project_code: str | None = None
    parent_summary: str | None = None
    existing_components: list[str] = Field(default_factory=list)
    org_context: OrgContext
    clarification_answers: list[ClarificationAnswer] = Field(default_factory=list)


class DecomposeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    correlation_id: str
    work_item_id: str
    analysis: "AnalysisResponse"
    matched_template: "ProcessTemplateSpec | None" = None
    max_items: int = Field(default=12, le=25)
    org_context: OrgContext


class ProcessTemplateSpec(BaseModel):
    """A seeded process template the decomposer should customize rather than invent from."""

    model_config = ConfigDict(extra="forbid")

    code: str
    name: str
    description: str | None = None
    typical_task_titles: list[str] = Field(default_factory=list)


class ExtractSkillsRequest(BaseModel):
    """POST /internal/v1/extract-skills — skill and effort extraction for a single item."""

    model_config = ConfigDict(extra="forbid")

    correlation_id: str
    work_item_id: str
    summary: str = Field(min_length=5, max_length=255)
    description: str | None = Field(default=None, max_length=20_000)
    work_item_type: WorkItemType
    org_context: OrgContext


class DepartmentLoad(BaseModel):
    model_config = ConfigDict(extra="forbid")

    department_name: str
    total_capacity_hours: float
    assigned_hours: float


class ParentProgress(BaseModel):
    model_config = ConfigDict(extra="forbid")

    reference_key: str
    progress_percent: float


class SummarizeReportRequest(BaseModel):
    """POST /internal/v1/summarize-report — the weekly report's persisted metrics, and
    nothing else. No work item text, no per-employee counts: see
    `ReportSummaryResponse` for why that is a hard boundary, not a style choice."""

    model_config = ConfigDict(extra="forbid")

    period_start: str
    period_end: str
    status_counts: dict[str, int] = Field(default_factory=dict)
    completed_count: int = Field(ge=0)
    blocked_count: int = Field(ge=0)
    overdue_count: int = Field(ge=0)
    sla_risk_count: int = Field(ge=0)
    reassignment_count: int = Field(ge=0)
    approval_change_count: int = Field(ge=0)
    department_workload: list[DepartmentLoad] = Field(default_factory=list)
    parent_ticket_progress: list[ParentProgress] = Field(default_factory=list)


# Deferred imports to avoid a circular module dependency (AnalysisResponse references
# nothing in requests.py, but requests.py embeds it for re-analysis / decomposition input).
from app.schemas.responses import AnalysisResponse  # noqa: E402

AnalyzeTicketRequest.model_rebuild()
DecomposeRequest.model_rebuild()
