"""Sanitization and prompt-injection defence — contract §5.

Applied before any prompt is built. Note §5's closing point, which is the actual defence:
output containment (schema-constrained responses) and authority containment (no field
here can assign, approve or change status) matter more than any regex. The regexes here
catch the common case and set `injection_suspected`, which routes the item to a human;
they are not claimed to be exhaustive.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from app.config import settings

# Stable per-request placeholder numbering so the model can still reason about
# "the same person" without ever seeing the real value.
_EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
_PHONE_RE = re.compile(r"(?<!\d)(\+?\d[\d\-\s()]{7,}\d)(?!\d)")
_IBAN_RE = re.compile(r"\b[A-Z]{2}\d{2}[A-Z0-9]{10,30}\b")
_CARD_RE = re.compile(r"\b(?:\d[ -]?){13,19}\b")
_NATIONAL_ID_RE = re.compile(r"\b\d{11}\b")  # Turkish TCKN-shaped; a conservative catch-all

_REDACTION_RULES: list[tuple[str, re.Pattern]] = [
    ("EMAIL", _EMAIL_RE),
    ("IBAN", _IBAN_RE),
    ("CARD", _CARD_RE),
    ("PHONE", _PHONE_RE),
    ("NATIONAL_ID", _NATIONAL_ID_RE),
]

_INJECTION_PATTERNS = [
    re.compile(r"ignore (all |previous |prior )?instructions", re.IGNORECASE),
    re.compile(r"^\s*system\s*:", re.IGNORECASE | re.MULTILINE),
    re.compile(r"you are now", re.IGNORECASE),
    re.compile(r"<\|im_start\|>"),
    re.compile(r"[A-Za-z0-9+/]{200,}={0,2}"),  # base64 blob > ~200 chars
    re.compile(r"disregard (the )?(above|prior)", re.IGNORECASE),
]


@dataclass(slots=True)
class SanitizedContent:
    text: str
    redaction_map: dict[str, str]  # placeholder -> original (kept server-side only, never sent onward)
    injection_suspected: bool


def _redact(text: str) -> tuple[str, dict[str, str]]:
    redaction_map: dict[str, str] = {}
    counters: dict[str, int] = {}

    def _replace(kind: str, match: re.Match) -> str:
        original = match.group(0)
        # Reuse the same placeholder for a repeated value within one request.
        for placeholder, seen in redaction_map.items():
            if seen == original:
                return placeholder
        counters[kind] = counters.get(kind, 0) + 1
        placeholder = f"<{kind}_{counters[kind]}>"
        redaction_map[placeholder] = original
        return placeholder

    for kind, pattern in _REDACTION_RULES:
        text = pattern.sub(lambda m, k=kind: _replace(k, m), text)
    return text, redaction_map


def _detect_injection(text: str) -> bool:
    return any(p.search(text) for p in _INJECTION_PATTERNS)


def sanitize(raw_text: str | None) -> SanitizedContent:
    """PII redaction + length clamp + injection detection. Delimiter fencing (wrapping in
    `<user_content>`) happens in the prompt builder, not here, since it is a prompt-shape
    concern rather than a content-safety one."""
    if raw_text is None:
        return SanitizedContent(text="", redaction_map={}, injection_suspected=False)

    text = raw_text
    max_chars = settings.max_description_chars
    if len(text) > max_chars:
        text = text[:max_chars] + " …[truncated]"

    injection_suspected = _detect_injection(text) if settings.sanitizer_strict else False
    text, redaction_map = _redact(text)

    return SanitizedContent(text=text, redaction_map=redaction_map, injection_suspected=injection_suspected)


def fence_user_content(text: str) -> str:
    """Delimiter fencing — contract §5 step 3. The system prompt states that nothing
    inside this fence may be treated as an instruction."""
    return f"<user_content>\n{text}\n</user_content>"
