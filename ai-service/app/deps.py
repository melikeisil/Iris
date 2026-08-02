"""Service-token authentication — contract §2: "Callable only by the backend."

`X-Service-Token` is compared in constant time so a timing side-channel can't be used to
brute-force it character by character, the same reasoning applied to login in the backend
(`AuthService`). This is the *only* auth this service has — see the boundaries in
contract §2, none of which are optional.
"""

from __future__ import annotations

import hmac

from fastapi import Header, HTTPException, status

from app.config import settings


async def require_service_token(x_service_token: str = Header(default="")) -> None:
    if not settings.service_token:
        # Fails startup in main.py's lifespan handler; this is a defence-in-depth
        # second check in case that ever changes.
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "service token not configured")
    if not hmac.compare_digest(x_service_token, settings.service_token):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "invalid or missing X-Service-Token")
