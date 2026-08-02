"""POST /internal/v1/decompose — contract §6, "Decomposition schema" and §4, `DecomposeRequest`.

Reuses `analyzer._run_with_repair` (the same provider-agnostic validation/repair kernel
used by ticket and development analysis) so decomposition gets the identical one-shot
repair guarantee, then layers the decomposition-specific structural checks from
`schemas/validators.py` (acyclic dependency graph, vocabulary membership, mandatory
testing coverage) as the `post_validate` step.
"""

from __future__ import annotations

import json

from app.providers.base import LLMProvider
from app.schemas.requests import DecomposeRequest
from app.schemas.responses import DecomposeEnvelope, DecomposeResponse
from app.schemas.validators import validate_decomposition_set
from app.services import prompt_builder
from app.services.analyzer import _run_with_repair
from app.services.sanitizer import fence_user_content
from app.services.skill_resolver import resolve_skills


async def decompose(request: DecomposeRequest, provider: LLMProvider) -> DecomposeEnvelope:
    examples = prompt_builder.load_examples("decomposition_profile_photo.json")
    system_prompt = prompt_builder.render(
        "decomposition.j2",
        org_context=request.org_context,
        examples=examples,
        analysis=request.analysis,
        matched_template=request.matched_template,
        max_items=request.max_items,
    )
    payload = {
        "analysis": request.analysis.model_dump(),
        "matched_template": request.matched_template.model_dump() if request.matched_template else None,
    }
    user_content = fence_user_content(json.dumps(payload, indent=2, default=str))

    def post_validate(decomposition: DecomposeResponse) -> list[str]:
        items = decomposition.suggested_work_items[: request.max_items]
        decomposition.suggested_work_items = items
        warnings = validate_decomposition_set(
            items, request.org_context, request.analysis.work_family, request.analysis.estimated_total_hours
        )
        # Alias resolution — unmatched skills move to unresolved_skills, they never fail
        # the item (contract §6).
        for item in items:
            resolved, unresolved = resolve_skills(item.required_skills, request.org_context.skill_codes)
            item.required_skills = resolved
            item.unresolved_skills = unresolved
        return warnings

    parsed, meta, warnings = await _run_with_repair(
        provider,
        system_prompt=system_prompt,
        user_content=user_content,
        response_schema=DecomposeResponse,
        post_validate=post_validate,
        max_tokens=8192,
        temperature=0.1,
    )
    decomposition: DecomposeResponse = parsed
    decomposition.work_item_id = request.work_item_id
    decomposition.warnings = [*decomposition.warnings, *warnings]
    return DecomposeEnvelope(decomposition=decomposition, meta=meta)
