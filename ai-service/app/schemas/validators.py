"""Cross-item structural checks that a single Pydantic model cannot express, because they
depend on the whole decomposition set plus `org_context` — see contract §6, "Additional
structural checks on a decomposition set".

Per-field rules (vagueness, Fibonacci points, response-shape) live directly on the models
in `responses.py`, where Pydantic can enforce them automatically. This module holds only
what needs the *set*.
"""

from __future__ import annotations

from app.schemas.requests import OrgContext
from app.schemas.responses import SuggestedWorkItem, WorkFamily


class DecompositionValidationError(ValueError):
    pass


def _team_lookup(org_context: OrgContext) -> dict[str, str]:
    """team_code -> department_code"""
    return {t.code: t.department_code for t in org_context.team_codes}


def validate_dependencies_reference_existing_items(items: list[SuggestedWorkItem]) -> None:
    known = {i.temp_id for i in items}
    for item in items:
        for dep in item.dependencies:
            if dep not in known:
                raise DecompositionValidationError(
                    f"'{item.temp_id}' depends on unknown temp_id '{dep}'"
                )


def validate_dependency_graph_acyclic(items: list[SuggestedWorkItem]) -> None:
    graph = {i.temp_id: list(i.dependencies) for i in items}
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {k: WHITE for k in graph}

    def visit(node: str, path: list[str]) -> None:
        color[node] = GRAY
        for dep in graph.get(node, []):
            if color.get(dep, WHITE) == GRAY:
                cycle = " -> ".join([*path, node, dep])
                raise DecompositionValidationError(f"dependency cycle detected: {cycle}")
            if color.get(dep, WHITE) == WHITE:
                visit(dep, [*path, node])
        color[node] = BLACK

    for node in graph:
        if color[node] == WHITE:
            visit(node, [])


def validate_department_codes(items: list[SuggestedWorkItem], org_context: OrgContext) -> None:
    valid = set(org_context.department_codes)
    for item in items:
        if item.department_code not in valid:
            raise DecompositionValidationError(
                f"'{item.temp_id}' names department '{item.department_code}' "
                f"which is not in org_context"
            )


def validate_team_belongs_to_department(items: list[SuggestedWorkItem], org_context: OrgContext) -> None:
    team_dept = _team_lookup(org_context)
    for item in items:
        if item.team_code is None:
            continue
        owning_dept = team_dept.get(item.team_code)
        if owning_dept is None:
            raise DecompositionValidationError(
                f"'{item.temp_id}' names team '{item.team_code}' which does not exist"
            )
        if owning_dept != item.department_code:
            raise DecompositionValidationError(
                f"'{item.temp_id}' assigns team '{item.team_code}' (dept {owning_dept}) "
                f"to department '{item.department_code}'"
            )


def hours_budget_warning(items: list[SuggestedWorkItem], estimated_total_hours: float | None) -> str | None:
    """A warning, not a failure — the contract explicitly downgrades this one."""
    if estimated_total_hours is None or not items:
        return None
    total = sum(i.estimated_hours for i in items)
    low, high = estimated_total_hours * 0.4, estimated_total_hours * 1.6
    if not (low <= total <= high):
        return (
            f"decomposition totals {total:.1f}h, outside ±60% of the parent's "
            f"estimated {estimated_total_hours:.1f}h"
        )
    return None


def validate_development_work_has_testing(items: list[SuggestedWorkItem], work_family: WorkFamily) -> None:
    if work_family != WorkFamily.DEVELOPMENT_WORK:
        return
    has_test_item = any(i.work_item_type.value == "TEST" for i in items)
    has_testing_requirements = any(i.testing_requirements for i in items)
    if not (has_test_item or has_testing_requirements):
        raise DecompositionValidationError(
            "DEVELOPMENT_WORK decomposition must include a TEST item or non-empty "
            "testing_requirements on at least one item"
        )


def validate_decomposition_set(
    items: list[SuggestedWorkItem],
    org_context: OrgContext,
    work_family: WorkFamily,
    estimated_total_hours: float | None,
) -> list[str]:
    """Runs every structural check; returns non-fatal warnings. Raises
    `DecompositionValidationError` on the first fatal violation."""
    validate_dependencies_reference_existing_items(items)
    validate_dependency_graph_acyclic(items)
    validate_department_codes(items, org_context)
    validate_team_belongs_to_department(items, org_context)
    validate_development_work_has_testing(items, work_family)

    warnings: list[str] = []
    warning = hours_budget_warning(items, estimated_total_hours)
    if warning:
        warnings.append(warning)
    return warnings
