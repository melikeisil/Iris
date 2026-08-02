"""Python mirrors of the Java domain enums.

These must stay byte-identical (member names, not values) to
`com.smartwork.workitem.domain.*` and `com.smartwork.common.security.*` — Spring Boot
deserializes this service's JSON straight into the Java enums with no translation layer.
A renamed member here is a silent contract break, which is why `test_schemas.py` diffs
this file's member lists against fixtures generated from the Java source at Phase 3.
"""

from __future__ import annotations

from enum import StrEnum


class WorkItemType(StrEnum):
    REQUEST = "REQUEST"
    INCIDENT = "INCIDENT"
    EPIC = "EPIC"
    FEATURE = "FEATURE"
    STORY = "STORY"
    BUG = "BUG"
    TASK = "TASK"
    TECHNICAL_DEBT = "TECHNICAL_DEBT"
    RESEARCH = "RESEARCH"
    TEST = "TEST"
    DOCUMENTATION = "DOCUMENTATION"
    SUBTASK = "SUBTASK"


class WorkFamily(StrEnum):
    SERVICE_WORK = "SERVICE_WORK"
    PROCESS_WORK = "PROCESS_WORK"
    DEVELOPMENT_WORK = "DEVELOPMENT_WORK"
    INCIDENT_WORK = "INCIDENT_WORK"
    GENERAL_OPERATIONAL_WORK = "GENERAL_OPERATIONAL_WORK"


class OriginType(StrEnum):
    USER_CREATED = "USER_CREATED"
    JIRA_IMPORTED = "JIRA_IMPORTED"
    TICKET_GENERATED = "TICKET_GENERATED"
    AI_GENERATED = "AI_GENERATED"
    PROCESS_TEMPLATE = "PROCESS_TEMPLATE"
    PROJECT_PLANNING = "PROJECT_PLANNING"
    INCIDENT_GENERATED = "INCIDENT_GENERATED"
    RECURRING = "RECURRING"


class Priority(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class RiskLevel(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class Complexity(StrEnum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class ClosureType(StrEnum):
    AUTO_CLOSE = "AUTO_CLOSE"
    REQUESTER_VERIFICATION = "REQUESTER_VERIFICATION"
    PROCESS_OWNER_VERIFICATION = "PROCESS_OWNER_VERIFICATION"
    TEAM_LEAD_VERIFICATION = "TEAM_LEAD_VERIFICATION"


class SlaRisk(StrEnum):
    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"


class BusinessImpact(StrEnum):
    NONE = "NONE"
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class ClarificationField(StrEnum):
    SCOPE = "scope"
    ACCEPTANCE_CRITERIA = "acceptanceCriteria"
    PRIORITY = "priority"
    IMPACT = "impact"
    ENVIRONMENT = "environment"
    DEADLINE = "deadline"
    BUDGET = "budget"
    OTHER = "other"


FIBONACCI_STORY_POINTS: frozenset[int] = frozenset({1, 2, 3, 5, 8, 13, 21})
