from __future__ import annotations

import pytest

from app.schemas.requests import OrgContext, TeamRef


@pytest.fixture
def org_context() -> OrgContext:
    """A trimmed mirror of db/demo/V900__demo_data.sql's vocabulary — enough to exercise
    every validator without depending on the backend's seed data."""
    return OrgContext(
        department_codes=["HR", "IT", "FINANCE", "ENGINEERING"],
        team_codes=[
            TeamRef(code="SUPPORT", department_code="IT"),
            TeamRef(code="BACKEND", department_code="ENGINEERING"),
            TeamRef(code="FRONTEND", department_code="ENGINEERING"),
            TeamRef(code="PLATFORM", department_code="ENGINEERING"),
            TeamRef(code="HR_OPS", department_code="HR"),
            TeamRef(code="FINANCE_OPS", department_code="FINANCE"),
        ],
        project_codes=["IDENTITY_PLATFORM", "PAYMENTS", "INTERNAL_TOOLS"],
        component_codes=["profile-api", "frontend-profile", "object-storage", "payment-gateway"],
        skill_codes=[
            "JAVA", "TYPESCRIPT", "PYTHON", "SQL", "SPRING_BOOT", "SPRING_SECURITY",
            "REACT", "HIBERNATE", "FASTAPI", "POSTGRESQL", "OBJECT_STORAGE", "DOCKER",
            "CI_CD", "MICROSOFT_365", "ACTIVE_DIRECTORY", "REST_API", "SECURITY",
            "IDENTITY_MANAGEMENT", "PAYMENTS", "ACCESSIBILITY", "HARDWARE_SUPPORT",
            "USER_PROVISIONING", "HR_ONBOARDING", "PAYROLL", "PROCUREMENT",
            "UNIT_TESTING", "INTEGRATION_TESTING", "CODE_REVIEW", "INCIDENT_RESPONSE",
            "JIRA_ADMINISTRATION", "GIT",
        ],
        process_template_codes=["EMPLOYEE_ONBOARDING"],
        category_codes=["hr_onboarding", "feature", "defect_report", "general"],
    )
