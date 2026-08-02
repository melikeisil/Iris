"""Environment-driven configuration.

The service refuses to start without a `SERVICE_TOKEN` (see `deps.py`) for the same
reason the backend's `StartupSecretValidator` refuses to start with a weak JWT secret:
a misconfigured secret should fail loudly at boot, not silently accept every caller.
"""

from __future__ import annotations

from enum import StrEnum

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ProviderChoice(StrEnum):
    AUTO = "auto"
    ANTHROPIC = "anthropic"
    GEMINI = "gemini"
    MOCK = "mock"


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    llm_provider: ProviderChoice = ProviderChoice.AUTO
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-sonnet-5"
    gemini_api_key: str = ""
    gemini_model: str = "gemini-2.5-flash"
    llm_timeout_seconds: int = 45
    llm_max_retries: int = 2
    demo_mode: bool = False

    service_token: str = Field(default="")

    prompt_version: str = "v1"
    log_level: str = "INFO"
    log_format: str = "json"
    sanitizer_strict: bool = True
    max_description_chars: int = 20_000

    @field_validator("service_token")
    @classmethod
    def _non_empty_in_non_test(cls, v: str) -> str:
        # Deliberately not enforced here: enforced once, at startup, in main.py's
        # lifespan handler, so the failure mode is "process refuses to start" rather
        # than "field silently accepts an empty string". See main.py.
        return v


settings = Settings()
