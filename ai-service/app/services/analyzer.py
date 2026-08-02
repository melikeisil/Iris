"""Orchestration + the validation/repair loop — contract §8.

`_run_with_repair` is deliberately provider-agnostic and schema-agnostic: it is the same
function whether the caller is analyzing a ticket, analyzing development work, or
decomposing an accepted analysis. Exactly one repair attempt. A second failure is a
signal, not something to grind against — it is raised as `AnalysisFailedError` for the
API layer to turn into a 422 with structured errors, per contract §8's closing line.
"""

from __future__ import annotations

import hashlib
import json
import logging
from typing import Callable

import httpx
from pydantic import BaseModel, ValidationError

from app.config import settings
from app.providers.base import LLMProvider
from app.providers.mock_provider import MockProvider
from app.schemas.requests import (
    AnalyzeDevelopmentRequest,
    AnalyzeTicketRequest,
    ExtractSkillsRequest,
    OrgContext,
)
from app.schemas.responses import (
    AnalysisEnvelope,
    AnalysisResponse,
    ExtractedSkill,
    ExtractSkillsEnvelope,
    ExtractSkillsResponse,
    ProviderMeta,
)
from app.schemas.validators import DecompositionValidationError
from app.services import prompt_builder
from app.services.sanitizer import fence_user_content, sanitize
from app.services.skill_resolver import resolve_skills

logger = logging.getLogger("smartwork.ai.analyzer")


class AnalysisFailedError(Exception):
    """Raised after the single repair attempt also fails. Carries exactly what
    contract §8 specifies for the 422 body: errors, a hash of the last raw output
    (never the raw output itself — it may contain redacted-but-still-sensitive content),
    and the attempt count."""

    def __init__(self, errors: list[str], raw_output: str, attempts: int, injection_suspected: bool = False):
        super().__init__("; ".join(errors) or "validation failed")
        self.errors = errors
        self.raw_output_hash = hashlib.sha256(raw_output.encode("utf-8")).hexdigest()
        self.attempts = attempts
        self.injection_suspected = injection_suspected


def _try_validate(
    response_schema: type[BaseModel],
    raw: str,
    post_validate: Callable[[BaseModel], list[str]],
) -> tuple[list[str], BaseModel | None, list[str]]:
    try:
        parsed = response_schema.model_validate_json(raw)
    except ValidationError as e:
        return [f"{'.'.join(str(p) for p in err['loc'])}: {err['msg']}" for err in e.errors()], None, []
    except json.JSONDecodeError as e:
        return [f"invalid JSON: {e}"], None, []

    try:
        warnings = post_validate(parsed) or []
    except (ValueError, DecompositionValidationError) as e:
        return [str(e)], None, []
    return [], parsed, warnings


def _looks_truncated(raw: str, errors: list[str]) -> bool:
    lowered = " ".join(errors).lower()
    if "eof while parsing" in lowered or "unexpected end" in lowered:
        return True
    stripped = raw.rstrip()
    if stripped and stripped[0] in "[{" and stripped[-1] not in "]}":
        return True
    return False


def _is_rate_limited(exc: Exception) -> bool:
    return isinstance(exc, httpx.HTTPStatusError) and exc.response.status_code == 429


async def _run_with_repair(
    provider: LLMProvider,
    *,
    system_prompt: str,
    user_content: str,
    response_schema: type[BaseModel],
    post_validate: Callable[[BaseModel], list[str]],
    max_tokens: int = 4096,
    temperature: float = 0.2,
) -> tuple[BaseModel, ProviderMeta, list[str]]:
    raw, meta = await provider.complete_structured(
        system_prompt, user_content, response_schema, max_tokens, temperature
    )
    errors, parsed, warnings = _try_validate(response_schema, raw, post_validate)
    if parsed is not None:
        meta.attempts = 1
        return parsed, meta, warnings

    logger.warning("validation_failed_attempt", extra={"attempt": 1, "errors": errors})

    if _looks_truncated(raw, errors):
        repair_content = (
            f"{user_content}\n\n"
            "Previous output was truncated or malformed JSON. Regenerate the full response "
            "from scratch as one complete JSON object that satisfies the schema exactly. "
            "Do not continue the previous output."
        )
    else:
        repair_content = prompt_builder.render("repair.j2", raw_output=raw, errors=errors)
    raw2, meta2 = await provider.complete_structured(
        system_prompt, repair_content, response_schema, max_tokens, 0.0
    )
    errors2, parsed2, warnings2 = _try_validate(response_schema, raw2, post_validate)
    if parsed2 is not None:
        meta2.attempts = 2
        return parsed2, meta2, warnings2

    logger.warning("validation_failed_final", extra={"attempt": 2, "errors": errors2})
    raise AnalysisFailedError(errors=errors2, raw_output=raw2, attempts=2)


async def _run_with_runtime_fallback(
    provider: LLMProvider,
    *,
    system_prompt: str,
    user_content: str,
    response_schema: type[BaseModel],
    post_validate: Callable[[BaseModel], list[str]],
    max_tokens: int = 4096,
    temperature: float = 0.2,
) -> tuple[BaseModel, ProviderMeta, list[str]]:
    try:
        return await _run_with_repair(
            provider,
            system_prompt=system_prompt,
            user_content=user_content,
            response_schema=response_schema,
            post_validate=post_validate,
            max_tokens=max_tokens,
            temperature=temperature,
        )
    except AnalysisFailedError:
        raise
    except Exception as exc:
        if provider.is_mock or not _is_rate_limited(exc):
            raise
        if not settings.demo_mode:
            raise
        logger.warning(
            "provider_rate_limited_demo_fallback",
            extra={
                "provider": provider.name,
                "model": provider.model,
                "error_type": type(exc).__name__,
                "error": str(exc),
            },
        )
        parsed, meta, warnings = await _run_with_repair(
            MockProvider(),
            system_prompt=system_prompt,
            user_content=user_content,
            response_schema=response_schema,
            post_validate=post_validate,
            max_tokens=max_tokens,
            temperature=temperature,
        )
        return parsed, meta.model_copy(update={
            "provider": "demo-fallback",
            "model": "mock-deterministic-v1",
            "is_mock": True,
        }), warnings


def _validate_analysis_vocabulary(analysis: AnalysisResponse, org_context: OrgContext) -> None:
    if org_context.department_codes:
        for dept in analysis.departments:
            if dept.department_code not in org_context.department_codes:
                raise ValueError(
                    f"department '{dept.department_code}' is not in org_context.department_codes"
                )
    if org_context.project_codes and analysis.project_code is not None:
        if analysis.project_code not in org_context.project_codes:
            raise ValueError(f"project '{analysis.project_code}' is not in org_context.project_codes")


async def analyze_ticket(request: AnalyzeTicketRequest, provider: LLMProvider) -> AnalysisEnvelope:
    sanitized_summary = sanitize(request.summary)
    sanitized_description = sanitize(request.description)
    injection_suspected = sanitized_summary.injection_suspected or sanitized_description.injection_suspected

    examples = prompt_builder.load_examples("ticket_onboarding.json", "ticket_payments_defect.json")
    system_prompt = prompt_builder.render(
        "ticket_analysis.j2", org_context=request.org_context, examples=examples
    )
    payload = {
        "summary": sanitized_summary.text,
        "description": sanitized_description.text,
        "origin_type": request.origin_type.value,
        "priority_hint": request.priority_hint.value if request.priority_hint else None,
        "reporter_department_code": request.reporter_department_code,
        "clarification_answers": [a.model_dump() for a in request.clarification_answers],
        "previous_analysis": request.previous_analysis.model_dump() if request.previous_analysis else None,
        "similar_items": [s.model_dump() for s in request.similar_items],
    }
    user_content = fence_user_content(json.dumps(payload, indent=2, default=str))

    def post_validate(analysis: AnalysisResponse) -> list[str]:
        _validate_analysis_vocabulary(analysis, request.org_context)
        return []

    parsed, meta, _ = await _run_with_runtime_fallback(
        provider,
        system_prompt=system_prompt,
        user_content=user_content,
        response_schema=AnalysisResponse,
        post_validate=post_validate,
        temperature=0.2,
    )
    analysis: AnalysisResponse = parsed
    if injection_suspected and not analysis.injection_suspected:
        analysis = analysis.model_copy(update={"injection_suspected": True})
    return AnalysisEnvelope(analysis=analysis, meta=meta)


async def analyze_development(
    request: AnalyzeDevelopmentRequest, provider: LLMProvider
) -> AnalysisEnvelope:
    sanitized_description = sanitize(request.description)
    sanitized_criteria = sanitize(request.acceptance_criteria)
    injection_suspected = sanitized_description.injection_suspected or sanitized_criteria.injection_suspected

    examples = prompt_builder.load_examples("development_profile_photo.json")
    system_prompt = prompt_builder.render(
        "development_analysis.j2", org_context=request.org_context, examples=examples
    )
    payload = {
        "work_item_type": request.work_item_type.value,
        "summary": request.summary,
        "description": sanitized_description.text,
        "acceptance_criteria": sanitized_criteria.text,
        "project_code": request.project_code,
        "parent_summary": request.parent_summary,
        "existing_components": request.existing_components,
        "clarification_answers": [a.model_dump() for a in request.clarification_answers],
    }
    user_content = fence_user_content(json.dumps(payload, indent=2, default=str))

    def post_validate(analysis: AnalysisResponse) -> list[str]:
        _validate_analysis_vocabulary(analysis, request.org_context)
        return []

    parsed, meta, _ = await _run_with_runtime_fallback(
        provider,
        system_prompt=system_prompt,
        user_content=user_content,
        response_schema=AnalysisResponse,
        post_validate=post_validate,
        temperature=0.2,
    )
    analysis: AnalysisResponse = parsed
    if injection_suspected and not analysis.injection_suspected:
        analysis = analysis.model_copy(update={"injection_suspected": True})
    return AnalysisEnvelope(analysis=analysis, meta=meta)


async def extract_skills(request: ExtractSkillsRequest, provider: LLMProvider) -> ExtractSkillsEnvelope:
    sanitized_description = sanitize(request.description)

    system_prompt = (
        "You are a skill and effort extraction system for SmartWork AI. You output only "
        "the requested JSON. You choose skill codes only from the vocabulary below; a "
        "skill you cannot match to the vocabulary belongs in unresolved_skills, not in "
        "skills. You never decide who performs the work.\n\n"
        f"Skills: {', '.join(request.org_context.skill_codes)}"
    )
    payload = {
        "work_item_type": request.work_item_type.value,
        "summary": request.summary,
        "description": sanitized_description.text,
    }
    user_content = fence_user_content(json.dumps(payload, indent=2, default=str))

    def post_validate(extraction: ExtractSkillsResponse) -> list[str]:
        resolved_skills: list[ExtractedSkill] = []
        unresolved: list[str] = []
        for proposed in extraction.skills:
            matches, misses = resolve_skills([proposed.skill_code], request.org_context.skill_codes)
            if matches:
                resolved_skills.append(ExtractedSkill(skill_code=matches[0], confidence=proposed.confidence))
            else:
                unresolved.extend(misses)
        extraction.skills = resolved_skills
        extraction.unresolved_skills = [*extraction.unresolved_skills, *unresolved]
        return []

    parsed, meta, _ = await _run_with_runtime_fallback(
        provider,
        system_prompt=system_prompt,
        user_content=user_content,
        response_schema=ExtractSkillsResponse,
        post_validate=post_validate,
        temperature=0.2,
        max_tokens=1024,
    )
    return ExtractSkillsEnvelope(extraction=parsed, meta=meta)
