"""Loads the Jinja2 templates under app/prompts/ — contract §9.

Four blocks, in this fixed order, per template: role and constraints, vocabulary,
few-shot examples, user content (fenced, see `sanitizer.fence_user_content`).
"""

from __future__ import annotations

import json
from pathlib import Path

from jinja2 import Environment, FileSystemLoader, select_autoescape

_TEMPLATE_DIR = Path(__file__).resolve().parent.parent / "prompts"
_EXAMPLES_DIR = _TEMPLATE_DIR / "examples"

_env = Environment(
    loader=FileSystemLoader(str(_TEMPLATE_DIR)),
    autoescape=select_autoescape(disabled_extensions=(".j2",), default=False),
    trim_blocks=True,
    lstrip_blocks=True,
)


def render(template_name: str, **context) -> str:
    template = _env.get_template(template_name)
    return template.render(**context)


def load_examples(*filenames: str) -> str:
    """Loads few-shot examples from app/prompts/examples/*.json. Each file is asserted
    (in test_schemas.py) to validate against its own response schema, so a broken example
    can never silently teach the model a broken shape."""
    blocks = []
    for name in filenames:
        data = json.loads((_EXAMPLES_DIR / name).read_text())
        blocks.append(
            f"Input: {data['input_summary']}\nGood output:\n{json.dumps(data['good_output'], indent=2)}"
        )
    return "\n\n".join(blocks)


def example_path(filename: str) -> Path:
    return _EXAMPLES_DIR / filename


def all_example_files() -> list[Path]:
    return sorted(_EXAMPLES_DIR.glob("*.json"))
