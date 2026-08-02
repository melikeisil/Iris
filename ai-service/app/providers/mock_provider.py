"""The demo's actual engine — contract §7: "The mock is not a stub. It is the reason the
demo cannot fail on stage, and it is what makes the AI-path integration tests hermetic."

Deterministic on the normalized text it is given: same input, same output, every run, no
network. Three hand-authored fixtures cover the three demo scenarios in
docs/14-scope-and-roadmap.md §4 by keyword match; anything else falls back to a small
rule-based classifier so unscripted demo input still produces a plausible, schema-valid
answer instead of an error.
"""

from __future__ import annotations

import asyncio
import hashlib
import json
import re
import time
from typing import Any

from pydantic import BaseModel

from app.schemas.responses import AnalysisResponse, DecomposeResponse, ExtractSkillsResponse
from app.schemas.responses import ProviderMeta, ReportSummaryResponse

_SIMULATED_LATENCY_MS = 800


def _normalize(text: str) -> str:
    return re.sub(r"\s+", " ", text.strip().lower())


def _hash(text: str) -> str:
    return hashlib.sha256(_normalize(text).encode("utf-8")).hexdigest()[:16]


class MockProvider:
    name = "mock"
    model = "mock-deterministic-v1"
    is_mock = True

    async def complete_structured(
        self,
        system_prompt: str,
        user_content: str,
        response_schema: type[BaseModel],
        max_tokens: int = 4096,
        temperature: float = 0.2,
    ) -> tuple[str, ProviderMeta]:
        start = time.perf_counter()
        # Same latency profile as a real call, so loading states in the UI are exercised
        # rather than skipped in demo/test runs.
        await asyncio.sleep(_SIMULATED_LATENCY_MS / 1000)

        payload = self._build(response_schema, user_content)
        raw = json.dumps(payload, sort_keys=True)

        latency_ms = int((time.perf_counter() - start) * 1000)
        meta = ProviderMeta(
            provider=self.name,
            model=self.model,
            latency_ms=latency_ms,
            input_tokens=len(user_content.split()),
            output_tokens=len(raw.split()),
            is_mock=True,
            prompt_version="v1",
        )
        return raw, meta

    def _build(self, response_schema: type[BaseModel], user_content: str) -> dict[str, Any]:
        text = _normalize(user_content)
        if response_schema is AnalysisResponse:
            return _analyze(text)
        if response_schema is DecomposeResponse:
            return _decompose(text)
        if response_schema is ExtractSkillsResponse:
            return _extract_skills(text)
        if response_schema is ReportSummaryResponse:
            return _summarize_report(user_content)
        raise ValueError(f"MockProvider has no fixture logic for {response_schema!r}")


# ---------------------------------------------------------------------------
# Demo 1 — Employee Onboarding
# ---------------------------------------------------------------------------

def _is_onboarding(text: str) -> bool:
    return "onboarding" in text or ("new employee" in text and "start" in text)


def _onboarding_analysis(text: str) -> dict[str, Any]:
    has_answers = "start date" in text or "clarification_answers" in text and "role" in text
    # Re-analysis (clarification answers present) resolves the open questions and moves
    # straight to decomposition; the first pass asks for role + start date.
    if not has_answers:
        return {
            "work_family": "PROCESS_WORK",
            "work_item_type": "REQUEST",
            "category": "hr_onboarding",
            "summary": "Prepare onboarding activities for a new employee",
            "project_code": None,
            "recommended_team": "HR_OPS",
            "departments": [
                {"department_code": "HR", "confidence": 0.95,
                 "reason": "Onboarding is owned end-to-end by HR Operations."},
            ],
            "affected_components": [],
            "required_skills": ["HR_ONBOARDING"],
            "priority": "HIGH",
            "priority_score": 82,
            "risk_level": "LOW",
            "risk_signals": [],
            "complexity": "MEDIUM",
            "suggested_story_points": None,
            "requires_more_information": True,
            "clarification_questions": [
                {"id": "q1", "question": "What role is the new employee starting in?",
                 "field": "scope", "required": True},
                {"id": "q2", "question": "What is their confirmed start date?",
                 "field": "deadline", "required": True},
            ],
            "requires_decomposition": False,
            "matched_process_template": "EMPLOYEE_ONBOARDING",
            "estimated_total_hours": None,
            "confidence": 0.55,
            "suggested_work_items": [],
            "security_impact": False,
            "production_impact": False,
            "budget_impact": True,
            "compliance_impact": False,
            "sla_risk": "MEDIUM",
            "business_impact": "MEDIUM",
            "likely_duplicate_of": None,
            "likely_defect": False,
            "suggested_conversion": None,
            "injection_suspected": False,
            "explanation": (
                "Matches the EMPLOYEE_ONBOARDING process template, but role and start "
                "date are required to size equipment, access and desk-setup tasks."
            ),
        }
    return {
        "work_family": "PROCESS_WORK",
        "work_item_type": "REQUEST",
        "category": "hr_onboarding",
        "summary": "Prepare onboarding activities for a new employee starting Monday",
        "project_code": None,
        "recommended_team": "HR_OPS",
        "departments": [
            {"department_code": "HR", "confidence": 0.95,
             "reason": "Owns the onboarding process end-to-end."},
            {"department_code": "IT", "confidence": 0.9,
             "reason": "Provisions accounts, hardware and access."},
            {"department_code": "FINANCE", "confidence": 0.6,
             "reason": "Approves and processes the equipment purchase."},
        ],
        "affected_components": [],
        "required_skills": ["HR_ONBOARDING", "USER_PROVISIONING", "HARDWARE_SUPPORT"],
        "priority": "HIGH",
        "priority_score": 85,
        "risk_level": "LOW",
        "risk_signals": [],
        "complexity": "MEDIUM",
        "suggested_story_points": None,
        "requires_more_information": False,
        "clarification_questions": [],
        "requires_decomposition": True,
        "matched_process_template": "EMPLOYEE_ONBOARDING",
        "estimated_total_hours": 18.0,
        "confidence": 0.91,
        "suggested_work_items": [],
        "security_impact": False,
        "production_impact": False,
        "budget_impact": True,
        "compliance_impact": False,
        "sla_risk": "MEDIUM",
        "business_impact": "MEDIUM",
        "likely_duplicate_of": None,
        "likely_defect": False,
        "suggested_conversion": None,
        "injection_suspected": False,
        "explanation": (
            "Customized EMPLOYEE_ONBOARDING template with role and start date; ready for "
            "nine-task decomposition across HR, IT and Finance."
        ),
    }


def _onboarding_decomposition() -> dict[str, Any]:
    items = [
        ("t1", "Create HR personnel file and offer paperwork", "HR", "HR_OPS",
         ["HR_ONBOARDING"], 1.5, "LOW", []),
        ("t2", "Send welcome email with first-day logistics", "HR", "HR_OPS",
         ["HR_ONBOARDING"], 0.5, "LOW", ["t1"]),
        ("t3", "Provision Active Directory account and email", "IT", "SUPPORT",
         ["ACTIVE_DIRECTORY", "USER_PROVISIONING"], 1.0, "LOW", ["t1"]),
        ("t4", "Assign Microsoft 365 licence and mailbox", "IT", "SUPPORT",
         ["MICROSOFT_365", "USER_PROVISIONING"], 0.5, "LOW", ["t3"]),
        ("t5", "Order and configure laptop and peripherals", "IT", "SUPPORT",
         ["HARDWARE_SUPPORT"], 3.0, "MEDIUM", ["t1"]),
        ("t6", "Purchase desk setup and office equipment", "FINANCE", "FINANCE_OPS",
         ["PROCUREMENT"], 1.0, "MEDIUM", ["t1"]),
        ("t7", "Grant building and system access badges", "IT", "SUPPORT",
         ["USER_PROVISIONING"], 0.5, "LOW", ["t3"]),
        ("t8", "Schedule first-week onboarding meetings", "HR", "HR_OPS",
         ["HR_ONBOARDING"], 1.5, "LOW", ["t2"]),
        ("t9", "Confirm payroll setup and benefits enrollment", "FINANCE", "FINANCE_OPS",
         ["PAYROLL"], 2.0, "LOW", ["t1"]),
    ]
    work_items = []
    for temp_id, title, dept, team, skills, hours, risk, deps in items:
        work_items.append({
            "temp_id": temp_id,
            "title": title,
            "description": f"{title}, as part of the new hire's first-week onboarding plan.",
            "work_item_type": "TASK",
            "department_code": dept,
            "team_code": team,
            "required_skills": skills,
            "affected_components": [],
            "estimated_hours": hours,
            "suggested_story_points": None,
            "priority": "HIGH" if temp_id in ("t3", "t5") else "MEDIUM",
            "risk_level": risk,
            "dependencies": deps,
            "testing_requirements": [],
            "closure_type": "PROCESS_OWNER_VERIFICATION",
            "sensitive_flags": {
                "security_impact": False,
                "production_impact": False,
                "budget_impact": temp_id in ("t5", "t6"),
                "compliance_impact": False,
            },
            "confidence": 0.88,
            "explanation": f"Standard {dept} step of the EMPLOYEE_ONBOARDING template.",
        })
    return {"work_item_id": "", "suggested_work_items": work_items, "warnings": []}


# ---------------------------------------------------------------------------
# Demo 2 — Development Story (profile photo upload)
# ---------------------------------------------------------------------------

def _is_profile_photo(text: str) -> bool:
    return "profile photo" in text or ("upload" in text and "photo" in text)


def _profile_photo_analysis(text: str) -> dict[str, Any]:
    has_answers = "jpeg" in text or "png" in text or "5mb" in text or "migration" in text
    if not has_answers:
        return {
            "work_family": "DEVELOPMENT_WORK",
            "work_item_type": "STORY",
            "category": "feature",
            "summary": "Users must be able to upload a profile photo",
            "project_code": "IDENTITY_PLATFORM",
            "recommended_team": "BACKEND",
            "departments": [
                {"department_code": "ENGINEERING", "confidence": 0.9,
                 "reason": "Feature work on the Identity Platform."},
            ],
            "affected_components": ["profile-api", "frontend-profile", "object-storage"],
            "required_skills": ["SPRING_BOOT", "REACT", "OBJECT_STORAGE"],
            "priority": "MEDIUM",
            "priority_score": 45,
            "risk_level": "LOW",
            "risk_signals": [],
            "complexity": "MEDIUM",
            "suggested_story_points": 5,
            "requires_more_information": True,
            "clarification_questions": [
                {"id": "q1",
                 "question": "What file size and format limits should the upload accept?",
                 "field": "acceptanceCriteria", "required": True},
                {"id": "q2",
                 "question": "Does an existing photo need to be replaced or migrated?",
                 "field": "scope", "required": True},
            ],
            "requires_decomposition": False,
            "matched_process_template": None,
            "estimated_total_hours": None,
            "confidence": 0.52,
            "suggested_work_items": [],
            "security_impact": True,
            "production_impact": False,
            "budget_impact": False,
            "compliance_impact": False,
            "sla_risk": "NONE",
            "business_impact": "LOW",
            "likely_duplicate_of": None,
            "likely_defect": False,
            "suggested_conversion": None,
            "injection_suspected": False,
            "explanation": (
                "Acceptance criteria are incomplete: size/format limits and migration "
                "behaviour for existing photos are unstated, so decomposition would guess."
            ),
        }
    return {
        "work_family": "DEVELOPMENT_WORK",
        "work_item_type": "STORY",
        "category": "feature",
        "summary": "Users must be able to upload a profile photo",
        "project_code": "IDENTITY_PLATFORM",
        "recommended_team": "BACKEND",
        "departments": [
            {"department_code": "ENGINEERING", "confidence": 0.95,
             "reason": "Feature work on the Identity Platform."},
        ],
        "affected_components": ["profile-api", "frontend-profile", "object-storage"],
        "required_skills": ["SPRING_BOOT", "REACT", "OBJECT_STORAGE", "SECURITY"],
        "priority": "MEDIUM",
        "priority_score": 48,
        "risk_level": "LOW",
        "risk_signals": [],
        "complexity": "MEDIUM",
        "suggested_story_points": 5,
        "requires_more_information": False,
        "clarification_questions": [],
        "requires_decomposition": True,
        "matched_process_template": None,
        "estimated_total_hours": 24.0,
        "confidence": 0.87,
        "suggested_work_items": [],
        "security_impact": True,
        "production_impact": False,
        "budget_impact": False,
        "compliance_impact": False,
        "sla_risk": "NONE",
        "business_impact": "LOW",
        "likely_duplicate_of": None,
        "likely_defect": False,
        "suggested_conversion": None,
        "injection_suspected": False,
        "explanation": (
            "Size/format limits and no-migration-needed answers resolve the open "
            "questions; ready to decompose into backend, frontend, storage and test tasks."
        ),
    }


def _profile_photo_decomposition() -> dict[str, Any]:
    items = [
        ("t1", "Add POST /profile/avatar endpoint with size and MIME validation",
         "TASK", "ENGINEERING", "BACKEND", ["SPRING_BOOT", "REST_API", "SECURITY"],
         8.0, "MEDIUM", [], []),
        ("t2", "Provision object storage bucket and lifecycle policy for avatars",
         "TASK", "ENGINEERING", "PLATFORM", ["OBJECT_STORAGE"],
         4.0, "LOW", [], []),
        ("t3", "Build profile photo upload and crop UI",
         "TASK", "ENGINEERING", "FRONTEND", ["REACT"],
         8.0, "LOW", ["t1"], []),
        ("t4", "Write integration test for avatar upload end to end",
         "TEST", "ENGINEERING", "BACKEND", ["INTEGRATION_TESTING"],
         4.0, "LOW", ["t1", "t2", "t3"], ["Upload success", "Oversized file rejected", "Invalid MIME rejected"]),
    ]
    work_items = []
    for temp_id, title, wtype, dept, team, skills, hours, risk, deps, testing in items:
        work_items.append({
            "temp_id": temp_id,
            "title": title,
            "description": f"{title}. Part of the profile photo upload story.",
            "work_item_type": wtype,
            "department_code": dept,
            "team_code": team,
            "required_skills": skills,
            "affected_components": ["profile-api"] if "endpoint" in title else [],
            "estimated_hours": hours,
            "suggested_story_points": None,
            "priority": "MEDIUM",
            "risk_level": risk,
            "dependencies": deps,
            "testing_requirements": testing,
            "closure_type": "AUTO_CLOSE",
            "sensitive_flags": {
                "security_impact": "SECURITY" in skills,
                "production_impact": False,
                "budget_impact": False,
                "compliance_impact": False,
            },
            "confidence": 0.86,
            "explanation": "Standard slice of the profile-photo-upload decomposition.",
        })
    return {"work_item_id": "", "suggested_work_items": work_items, "warnings": []}


# ---------------------------------------------------------------------------
# Demo 3 — Ticket to Bug (payments)
# ---------------------------------------------------------------------------

def _is_payments_incident(text: str) -> bool:
    return "payment" in text and ("fail" in text or "checkout" in text or "intermittent" in text)


def _payments_analysis() -> dict[str, Any]:
    return {
        "work_family": "SERVICE_WORK",
        "work_item_type": "REQUEST",
        "category": "defect_report",
        "summary": "Payments fail intermittently during checkout",
        "project_code": "PAYMENTS",
        "recommended_team": "BACKEND",
        "departments": [
            {"department_code": "ENGINEERING", "confidence": 0.9,
             "reason": "Intermittent payment failures indicate a code-level defect."},
        ],
        "affected_components": ["payment-gateway"],
        "required_skills": ["PAYMENTS", "JAVA"],
        "priority": "CRITICAL",
        "priority_score": 92,
        "risk_level": "HIGH",
        "risk_signals": ["production checkout path affected", "revenue impact"],
        "complexity": "MEDIUM",
        "suggested_story_points": None,
        "requires_more_information": False,
        "clarification_questions": [],
        "requires_decomposition": False,
        "matched_process_template": None,
        "estimated_total_hours": None,
        "confidence": 0.83,
        "suggested_work_items": [],
        "security_impact": False,
        "production_impact": True,
        "budget_impact": False,
        "compliance_impact": False,
        "sla_risk": "HIGH",
        "business_impact": "CRITICAL",
        "likely_duplicate_of": None,
        "likely_defect": True,
        "suggested_conversion": "BUG",
        "injection_suspected": False,
        "explanation": (
            "Intermittent failure on the production checkout path is a code defect, not "
            "a request for new work; recommend converting to a BUG against Payments."
        ),
    }


def _payments_bug_decomposition() -> dict[str, Any]:
    items = [
        ("t1", "Root-cause the intermittent payment gateway failure", "TASK",
         "ENGINEERING", "BACKEND", ["PAYMENTS", "JAVA"], 6.0, "HIGH", [], []),
        ("t2", "Implement fix for the identified payment failure path", "TASK",
         "ENGINEERING", "BACKEND", ["PAYMENTS", "SPRING_BOOT"], 8.0, "HIGH", ["t1"], []),
        ("t3", "Add unit tests covering the failure condition", "TEST",
         "ENGINEERING", "BACKEND", ["UNIT_TESTING"], 3.0, "MEDIUM", ["t2"],
         ["Failure path returns a retryable error, not a silent drop"]),
        ("t4", "Add integration test against a simulated flaky gateway", "TEST",
         "ENGINEERING", "BACKEND", ["INTEGRATION_TESTING", "PAYMENTS"], 4.0, "MEDIUM",
         ["t2"], ["Checkout succeeds after a single transient gateway error"]),
        ("t5", "Regression test the full checkout flow before release", "TEST",
         "ENGINEERING", "BACKEND", ["INTEGRATION_TESTING"], 3.0, "MEDIUM",
         ["t3", "t4"], ["Full checkout flow passes end to end"]),
    ]
    work_items = []
    for temp_id, title, wtype, dept, team, skills, hours, risk, deps, testing in items:
        work_items.append({
            "temp_id": temp_id,
            "title": title,
            "description": f"{title}, blocking release of the checkout payment fix.",
            "work_item_type": wtype,
            "department_code": dept,
            "team_code": team,
            "required_skills": skills,
            "affected_components": ["payment-gateway"],
            "estimated_hours": hours,
            "suggested_story_points": None,
            "priority": "CRITICAL" if temp_id in ("t1", "t2") else "HIGH",
            "risk_level": risk,
            "dependencies": deps,
            "testing_requirements": testing,
            "closure_type": "TEAM_LEAD_VERIFICATION",
            "sensitive_flags": {
                "security_impact": False,
                "production_impact": temp_id == "t2",
                "budget_impact": False,
                "compliance_impact": False,
            },
            "confidence": 0.84,
            "explanation": "Standard root-cause/fix/test breakdown for a production payment defect.",
        })
    return {"work_item_id": "", "suggested_work_items": work_items, "warnings": []}


# ---------------------------------------------------------------------------
# Rule-based fallback — anything that is not one of the three scripted scenarios
# ---------------------------------------------------------------------------

_KEYWORD_SKILLS = {
    "java": "JAVA", "spring": "SPRING_BOOT", "react": "REACT", "typescript": "TYPESCRIPT",
    "python": "PYTHON", "sql": "SQL", "database": "POSTGRESQL", "postgres": "POSTGRESQL",
    "docker": "DOCKER", "security": "SECURITY", "auth": "SECURITY", "payment": "PAYMENTS",
    "storage": "OBJECT_STORAGE", "s3": "OBJECT_STORAGE", "identity": "IDENTITY_MANAGEMENT",
    "hardware": "HARDWARE_SUPPORT", "laptop": "HARDWARE_SUPPORT", "onboarding": "HR_ONBOARDING",
    "payroll": "PAYROLL", "procurement": "PROCUREMENT", "accessibility": "ACCESSIBILITY",
    "test": "UNIT_TESTING", "ci": "CI_CD", "jira": "JIRA_ADMINISTRATION", "git": "GIT",
}

_INCIDENT_WORDS = ("down", "outage", "broken", "crash", "fail", "error", "not working")
_URGENT_WORDS = ("urgent", "critical", "asap", "production", "blocked", "blocking")


def _generic_skills(text: str) -> list[str]:
    found = {code for word, code in _KEYWORD_SKILLS.items() if word in text}
    return sorted(found) or ["JAVA"]


def _analyze(text: str) -> dict[str, Any]:
    if _is_onboarding(text):
        return _onboarding_analysis(text)
    if _is_profile_photo(text):
        return _profile_photo_analysis(text)
    if _is_payments_incident(text):
        return _payments_analysis()
    return _generic_analysis(text)


def _generic_analysis(text: str) -> dict[str, Any]:
    is_incident = any(w in text for w in _INCIDENT_WORDS)
    is_urgent = any(w in text for w in _URGENT_WORDS)
    skills = _generic_skills(text)
    priority = "CRITICAL" if (is_incident and is_urgent) else ("HIGH" if is_incident or is_urgent else "MEDIUM")
    score = {"CRITICAL": 88, "HIGH": 65, "MEDIUM": 40, "LOW": 15}[priority]
    is_dev = any(w in text for w in ("feature", "endpoint", "ui", "frontend", "backend", "story", "code"))
    return {
        "work_family": "DEVELOPMENT_WORK" if is_dev else ("INCIDENT_WORK" if is_incident else "SERVICE_WORK"),
        "work_item_type": "TASK" if is_dev else ("INCIDENT" if is_incident else "REQUEST"),
        "category": "general",
        "summary": text[:200].capitalize() if text else "Untitled work item",
        "project_code": None,
        "recommended_team": None,
        "departments": [
            {"department_code": "ENGINEERING" if is_dev else "IT", "confidence": 0.6,
             "reason": "Keyword-based classification; no scripted fixture matched this input."},
        ],
        "affected_components": [],
        "required_skills": skills,
        "priority": priority,
        "priority_score": score,
        "risk_level": "HIGH" if is_incident else "LOW",
        "risk_signals": ["unrecognized input pattern; low-confidence fallback"] if is_incident else [],
        "complexity": "MEDIUM",
        "suggested_story_points": 3 if is_dev else None,
        "requires_more_information": False,
        "clarification_questions": [],
        "requires_decomposition": False,
        "matched_process_template": None,
        "estimated_total_hours": 8.0 if is_dev else None,
        "confidence": 0.55,
        "suggested_work_items": [],
        "security_impact": False,
        "production_impact": is_incident,
        "budget_impact": False,
        "compliance_impact": False,
        "sla_risk": "MEDIUM" if is_incident else "NONE",
        "business_impact": "MEDIUM" if is_incident else "LOW",
        "likely_duplicate_of": None,
        "likely_defect": is_incident,
        "suggested_conversion": "BUG" if is_incident else None,
        "injection_suspected": False,
        "explanation": (
            "No scripted demo fixture matched this input; classified by keyword rules "
            "(mock provider fallback, not a real model call)."
        ),
    }


def _decompose(text: str) -> dict[str, Any]:
    if _is_onboarding(text):
        return _onboarding_decomposition()
    if _is_profile_photo(text):
        return _profile_photo_decomposition()
    if _is_payments_incident(text):
        return _payments_bug_decomposition()
    return {
        "work_item_id": "",
        "suggested_work_items": [
            {
                "temp_id": "t1",
                "title": "Investigate and document the required change in detail",
                "description": "Analyze the request and produce a concrete implementation plan.",
                "work_item_type": "RESEARCH",
                "department_code": "ENGINEERING",
                "team_code": None,
                "required_skills": _generic_skills(text),
                "affected_components": [],
                "estimated_hours": 4.0,
                "suggested_story_points": None,
                "priority": "MEDIUM",
                "risk_level": "LOW",
                "dependencies": [],
                "testing_requirements": [],
                "closure_type": "AUTO_CLOSE",
                "sensitive_flags": {"security_impact": False, "production_impact": False,
                                     "budget_impact": False, "compliance_impact": False},
                "confidence": 0.5,
                "explanation": "Generic fallback decomposition; no scripted fixture matched this input.",
            },
            {
                "temp_id": "t2",
                "title": "Implement the change identified during investigation",
                "description": "Build the change described by the investigation task.",
                "work_item_type": "TASK",
                "department_code": "ENGINEERING",
                "team_code": None,
                "required_skills": _generic_skills(text),
                "affected_components": [],
                "estimated_hours": 8.0,
                "suggested_story_points": None,
                "priority": "MEDIUM",
                "risk_level": "LOW",
                "dependencies": ["t1"],
                "testing_requirements": ["Change verified against the investigation findings"],
                "closure_type": "AUTO_CLOSE",
                "sensitive_flags": {"security_impact": False, "production_impact": False,
                                     "budget_impact": False, "compliance_impact": False},
                "confidence": 0.5,
                "explanation": "Generic fallback decomposition; no scripted fixture matched this input.",
            },
        ],
        "warnings": ["fallback decomposition used; no demo fixture matched the input"],
    }


def _extract_skills(text: str) -> dict[str, Any]:
    skills = _generic_skills(text)
    return {
        "skills": [{"skill_code": s, "confidence": 0.75} for s in skills],
        "unresolved_skills": [],
        "estimated_hours": max(2.0, min(40.0, len(text.split()) / 5)),
        "confidence": 0.7,
        "explanation": "Keyword-matched against the canonical skill vocabulary (mock provider).",
    }


# ---------------------------------------------------------------------------
# Weekly report summary — restates the caller's own metrics, invents nothing, and
# never mentions an individual employee (the input contains none).
# ---------------------------------------------------------------------------

def _summarize_report(user_content: str) -> dict[str, Any]:
    unfenced = user_content.replace("<user_content>", "").replace("</user_content>", "").strip()
    try:
        data = json.loads(unfenced)
    except json.JSONDecodeError:
        data = {}

    completed = data.get("completed_count", 0)
    blocked = data.get("blocked_count", 0)
    overdue = data.get("overdue_count", 0)
    sla_risk = data.get("sla_risk_count", 0)
    reassignments = data.get("reassignment_count", 0)
    approvals = data.get("approval_change_count", 0)
    period_start = data.get("period_start", "this period's start")
    period_end = data.get("period_end", "this period's end")

    summary = (
        f"Between {period_start} and {period_end}, {completed} task(s) were completed. "
        f"{blocked} task(s) are currently blocked, {overdue} are overdue and {sla_risk} "
        f"are at SLA risk. {reassignments} reassignment(s) and {approvals} approval "
        "decision(s) were recorded this period."
    )

    actions: list[str] = []
    if blocked:
        actions.append(f"Review the {blocked} blocked task(s) and their listed blockers.")
    if overdue:
        actions.append(f"Follow up on the {overdue} overdue task(s).")
    if sla_risk:
        actions.append(f"Check in on the {sla_risk} task(s) approaching their due date.")
    if not actions:
        actions.append("No blocked, overdue or at-risk work this period.")

    return {"summary": summary, "recommended_actions": actions[:4]}
