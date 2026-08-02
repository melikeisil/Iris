from contextvars import ContextVar, Token


_bearer_token: ContextVar[str | None] = ContextVar("smartwork_bearer_token", default=None)


def set_bearer_token(value: str | None) -> Token:
    return _bearer_token.set(value)


def reset_bearer_token(token: Token) -> None:
    _bearer_token.reset(token)


def current_bearer_token() -> str | None:
    return _bearer_token.get()
