from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    smartwork_api_base_url: str = "http://backend:8080/api/v1"
    mcp_http_port: int = 9000
    request_timeout_seconds: float = 10.0
    log_level: str = "INFO"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    @property
    def normalized_base_url(self) -> str:
        return self.smartwork_api_base_url.rstrip("/")
