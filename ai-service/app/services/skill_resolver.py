"""Skill vocabulary resolution — contract §6: `required_skills` ⊆ `org_context.skill_codes`
after alias resolution; unmatched codes are moved to `unresolved_skills`, not rejected.

This service has no knowledge of the backend's `skill_aliases` table — it does a simple
case-insensitive, punctuation-normalized match against the canonical codes supplied in
`org_context`. The backend remains the source of truth for aliases; this is a best-effort
pass so an LLM writing "springboot" instead of "SPRING_BOOT" doesn't get flagged.
"""

from __future__ import annotations

import re


def _normalize(code: str) -> str:
    return re.sub(r"[^a-z0-9]", "", code.lower())


def resolve_skills(
    proposed: list[str], canonical_skill_codes: list[str]
) -> tuple[list[str], list[str]]:
    """Returns (resolved_canonical_codes, unresolved_originals)."""
    by_normalized = {_normalize(code): code for code in canonical_skill_codes}

    resolved: list[str] = []
    unresolved: list[str] = []
    for candidate in proposed:
        canonical = by_normalized.get(_normalize(candidate))
        if canonical is not None:
            if canonical not in resolved:
                resolved.append(canonical)
        else:
            unresolved.append(candidate)
    return resolved, unresolved
