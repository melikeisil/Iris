"""Response schemas — see docs/07-ai-service-contract.md §6.

The single most important line in this file: **no field here is named `assigneeUserId`,
`assignee`, `approve`, `authorize` or `route`.** Candidate ranking and routing happen in
Java, on stored data, with deterministic formulas. The model never sees the employee
list and never gets to decide who does the work or whether it is approved — see
`tests/test_no_authorization_fields.py` for the structural guarantee this file must keep.
"""

from __future__ import annotations

import re
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.enums import (
    FIBONACCI_STORY_POINTS,
    BusinessImpact,
    Complexity,
    Priority,
    RiskLevel,
    SlaRisk,
    WorkFamily,
    WorkItemType,
)

# ---------------------------------------------------------------------------
# The vagueness rule (contract §6, "The vagueness rule"). Enforced, not merely
# requested in the prompt: a title matching one of these fails validation, which
# feeds the exact error text back into the one-shot repair prompt.
# ---------------------------------------------------------------------------
VAGUE_PATTERNS = [
    r"^(work on|do|handle|fix|implement|update|check|review)\s+(the\s+)?"
    r"(backend|frontend|issue|problem|bug|ui|api|code|it|this|stuff|things?)\.?$",
    r"^(backend|frontend|testing|documentation|deployment)( work| tasks?)?\.?$",
    r"^(investigate|analyze|look into)( it| this| the issue)?\.?$",
]
_VAGUE_RE = [re.compile(p, re.IGNORECASE) for p in VAGUE_PATTERNS]


def reject_vague_title(v: str) -> str:
    normalized = v.strip().lower()
    if any(p.match(normalized) for p in _VAGUE_RE):
        raise ValueError(f"Vague task title rejected: '{v}'")
    if len(normalized.split()) < 3:
        raise ValueError(f"Task title too short to be actionable: '{v}'")
    return v


class Department(BaseModel):
    model_config = ConfigDict(extra="forbid")

    department_code: str
    confidence: float = Field(ge=0, le=1)
    reason: str = Field(min_length=10, max_length=500)


class ClarificationQuestion(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str
    question: str = Field(min_length=10, max_length=300)
    field: Literal[
        "scope", "acceptanceCriteria", "priority", "impact",
        "environment", "deadline", "budget", "other",
    ]
    required: bool = True


class SensitiveFlags(BaseModel):
    """Inputs to the policy engine, never decisions. Mirrors the item-level flags on
    `AnalysisResponse` so a decomposed child can carry its own sensitivity forward."""

    model_config = ConfigDict(extra="forbid")

    security_impact: bool = False
    production_impact: bool = False
    budget_impact: bool = False
    compliance_impact: bool = False


class SuggestedWorkItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    temp_id: str
    title: str = Field(min_length=10, max_length=160)
    description: str = Field(min_length=30, max_length=4000)
    work_item_type: WorkItemType
    department_code: str
    team_code: str | None = None
    required_skills: list[str] = Field(min_length=1)
    affected_components: list[str] = Field(default_factory=list)
    estimated_hours: float = Field(gt=0, le=80)
    suggested_story_points: int | None = None
    priority: Priority
    risk_level: RiskLevel
    dependencies: list[str] = Field(default_factory=list)
    testing_requirements: list[str] = Field(default_factory=list)
    closure_type: Literal[
        "AUTO_CLOSE", "REQUESTER_VERIFICATION", "PROCESS_OWNER_VERIFICATION", "TEAM_LEAD_VERIFICATION",
    ]
    sensitive_flags: SensitiveFlags = Field(default_factory=SensitiveFlags)
    confidence: float = Field(ge=0, le=1)
    explanation: str = Field(min_length=20, max_length=600)

    # populated by post-validation in services/skill_resolver.py, not by the model
    unresolved_skills: list[str] = Field(default_factory=list)

    @field_validator("title")
    @classmethod
    def _reject_vague(cls, v: str) -> str:
        return reject_vague_title(v)

    @field_validator("suggested_story_points")
    @classmethod
    def _fibonacci(cls, v: int | None) -> int | None:
        if v is not None and v not in FIBONACCI_STORY_POINTS:
            raise ValueError("story points must be Fibonacci")
        return v


class AnalysisResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")  # unknown fields are a hard failure

    work_family: WorkFamily
    work_item_type: WorkItemType
    category: str | None = None
    summary: str = Field(max_length=255)
    project_code: str | None = None
    recommended_team: str | None = None
    departments: list[Department] = Field(min_length=1, max_length=5)
    affected_components: list[str] = Field(default_factory=list)
    required_skills: list[str] = Field(default_factory=list)
    priority: Priority
    priority_score: int = Field(ge=0, le=100)
    risk_level: RiskLevel
    risk_signals: list[str] = Field(default_factory=list)
    complexity: Complexity
    suggested_story_points: int | None = None
    requires_more_information: bool
    clarification_questions: list[ClarificationQuestion] = Field(default_factory=list)
    requires_decomposition: bool
    matched_process_template: str | None = None
    estimated_total_hours: float | None = Field(default=None, gt=0, le=2000)
    confidence: float = Field(ge=0, le=1)
    suggested_work_items: list[SuggestedWorkItem] = Field(default_factory=list)

    # sensitivity signals — inputs to the policy engine, never decisions
    security_impact: bool = False
    production_impact: bool = False
    budget_impact: bool = False
    compliance_impact: bool = False
    sla_risk: SlaRisk = SlaRisk.NONE
    business_impact: BusinessImpact = BusinessImpact.NONE
    likely_duplicate_of: str | None = None
    likely_defect: bool = False
    suggested_conversion: WorkItemType | None = None
    injection_suspected: bool = False
    explanation: str = Field(min_length=20, max_length=1000)

    @field_validator("suggested_story_points")
    @classmethod
    def _fibonacci(cls, v: int | None) -> int | None:
        if v is not None and v not in FIBONACCI_STORY_POINTS:
            raise ValueError("story points must be Fibonacci")
        return v

    @model_validator(mode="after")
    def consistency(self) -> "AnalysisResponse":
        if self.requires_more_information and not self.clarification_questions:
            raise ValueError("requires_more_information=true needs at least one question")
        if not self.requires_more_information and self.clarification_questions:
            raise ValueError("clarification questions provided but more info not required")
        if self.requires_decomposition and self.confidence < 0.4:
            raise ValueError("cannot propose decomposition below 0.4 confidence")
        if self.work_family != WorkFamily.DEVELOPMENT_WORK and self.suggested_story_points:
            raise ValueError("story points only apply to development work")
        if self.risk_level == RiskLevel.CRITICAL and not self.risk_signals:
            raise ValueError("CRITICAL risk requires explicit signals")
        if self.priority == Priority.CRITICAL and self.priority_score < 80:
            raise ValueError("CRITICAL priority inconsistent with score")
        return self


class DecomposeResponse(BaseModel):
    """Response body for POST /internal/v1/decompose."""

    model_config = ConfigDict(extra="forbid")

    work_item_id: str
    suggested_work_items: list[SuggestedWorkItem]
    warnings: list[str] = Field(default_factory=list)  # e.g. hours-budget mismatch


class ExtractedSkill(BaseModel):
    model_config = ConfigDict(extra="forbid")

    skill_code: str
    confidence: float = Field(ge=0, le=1)


class ExtractSkillsResponse(BaseModel):
    """Response body for POST /internal/v1/extract-skills."""

    model_config = ConfigDict(extra="forbid")

    skills: list[ExtractedSkill] = Field(default_factory=list)
    unresolved_skills: list[str] = Field(default_factory=list)
    estimated_hours: float | None = Field(default=None, gt=0, le=200)
    confidence: float = Field(ge=0, le=1)
    explanation: str = Field(min_length=20, max_length=600)


class ProviderMeta(BaseModel):
    model_config = ConfigDict(extra="forbid")

    provider: str
    model: str
    latency_ms: int
    input_tokens: int | None = None
    output_tokens: int | None = None
    is_mock: bool
    attempts: int = 1
    prompt_version: str


class AnalysisEnvelope(BaseModel):
    """What every /analyze/* and /decompose endpoint actually returns: the validated
    payload plus the provenance metadata the backend persists on the recommendation."""

    model_config = ConfigDict(extra="forbid")

    analysis: AnalysisResponse
    meta: ProviderMeta


class DecomposeEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    decomposition: DecomposeResponse
    meta: ProviderMeta


class ExtractSkillsEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    extraction: ExtractSkillsResponse
    meta: ProviderMeta


class ReportSummaryResponse(BaseModel):
    """Response body for POST /internal/v1/summarize-report.

    Deliberately narrow: a short prose summary and a short list of recommended actions,
    both restatements of the metrics the backend sent — never a new field for a per-person
    figure. There is no `top_performer`, no `ranking`, no `score` here, and there must
    never be: docs/07 and the Manager report brief both forbid ranking or scoring
    individual employees, and a field existing here would be the first step toward one
    slipping into a prompt eventually. See `test_report_summarizer.py`.
    """

    model_config = ConfigDict(extra="forbid")

    summary: str = Field(min_length=10, max_length=1200)
    recommended_actions: list[str] = Field(default_factory=list, max_length=6)


class ReportSummaryEnvelope(BaseModel):
    model_config = ConfigDict(extra="forbid")

    summary: str
    recommended_actions: list[str]
    meta: ProviderMeta


class AnalysisValidationError(BaseModel):
    """422 body — see contract §8. A second failure is a signal, not something to grind
    against: it is surfaced to a human and counted, not retried further."""

    model_config = ConfigDict(extra="forbid")

    errors: list[str]
    raw_output_hash: str
    attempts: int
    injection_suspected: bool = False
