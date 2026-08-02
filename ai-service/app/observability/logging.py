"""Structured JSON logging — contract §11.

Every log line at INFO carries `correlation_id`, `work_item_id`, `endpoint`, `provider`,
`model`, `latency_ms` and token counts where available. Prompt and completion bodies are
never logged at INFO — only at DEBUG, which stays off outside local development, because
they contain redacted-but-still-sensitive business content (contract §11).
"""

from __future__ import annotations

import logging
import sys

from pythonjsonlogger import jsonlogger

from app.config import settings


def configure_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)

    if settings.log_format == "json":
        formatter = jsonlogger.JsonFormatter(
            "%(asctime)s %(name)s %(levelname)s %(message)s",
            rename_fields={"asctime": "timestamp", "levelname": "level"},
        )
    else:
        formatter = logging.Formatter(
            "%(asctime)s %(levelname)s %(name)s %(message)s"
        )
    handler.setFormatter(formatter)

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(settings.log_level.upper())

    # uvicorn's own loggers otherwise install a second, non-JSON handler.
    for name in ("uvicorn", "uvicorn.error", "uvicorn.access"):
        logging.getLogger(name).handlers = [handler]
        logging.getLogger(name).propagate = False
