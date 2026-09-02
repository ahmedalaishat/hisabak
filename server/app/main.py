"""Hisabak parse service.

One endpoint: text in, structured transaction fields out. It exists so devices without an
on-device model (most Android phones, every pre-A17 iPhone) get the same confirm-first parsing
the flagships get — and so the app can learn a regex template from the result and stop calling
this service for that bank format entirely.

**Message text is never logged or persisted.** The app's privacy policy promises SMS content is
only sent when the user opts in and is not retained; that promise is kept here, in code: request
bodies are excluded from access logs and no handler writes `text` anywhere.
"""

import logging
import os
import secrets
import time

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.providers.anthropic_provider import AnthropicProvider
from app.limits import Limiter, RateLimitExceeded
from app.providers.base import ParseProvider
from app.schema import ParseRequest, ParseResponse

# uvicorn configures its own loggers and leaves everything else silent, so without this the
# operational lines below are dropped. Kept to a single stream handler: no file, no rotation,
# nothing that could persist a request body by accident.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("hisabak")

API_TOKEN = os.getenv("HISABAK_API_TOKEN", "")
RATE_LIMIT_PER_MINUTE = int(os.getenv("HISABAK_RATE_LIMIT_PER_MINUTE", "30"))
# The ceiling that actually bounds the bill. At roughly $0.0006 a call, 2000/day is about
# $1.20/day worst case — orders of magnitude above real use, and a known number either way.
DAILY_BUDGET = int(os.getenv("HISABAK_DAILY_BUDGET", "2000"))
# Stops a slow drip that never trips the per-minute window.
DAILY_PER_IP = int(os.getenv("HISABAK_DAILY_PER_IP", "200"))

app = FastAPI(title="Hisabak parse service", docs_url=None, redoc_url=None)
_bearer = HTTPBearer(auto_error=False)
_provider: ParseProvider = AnthropicProvider()
_limiter = Limiter(
    per_minute=RATE_LIMIT_PER_MINUTE,
    per_ip_daily=DAILY_PER_IP,
    global_daily=DAILY_BUDGET,
)


def _authorize(
    request: Request,
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> str:
    """Shared-secret auth. The token ships in the app, so it identifies the client rather than the
    user — it exists to keep the endpoint off the open internet, not to authenticate a person."""
    if not API_TOKEN:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Service not configured")
    # compare_digest, not ==: a short-circuiting comparison leaks the token prefix through
    # response timing, and this token is the only thing protecting the endpoint.
    if credentials is None or not secrets.compare_digest(credentials.credentials, API_TOKEN):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Unauthorized")
    return request.client.host if request.client else "unknown"


def _rate_limit(caller: str) -> None:
    """Applies the per-minute, per-IP-daily, and global-daily tiers.

    The response never says which tier tripped: that would tell an abuser exactly how much
    headroom is left and how to pace around it.
    """
    try:
        _limiter.check(caller, time.monotonic())
    except RateLimitExceeded as exceeded:
        log.warning("rate limited scope=%s used_today=%d", exceeded.scope, _limiter.used_today)
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Rate limit exceeded") from exceeded


@app.get("/health")
async def health() -> dict[str, str]:
    """Doubles as the client's availability probe — reachable means the parser is usable."""
    return {"status": "ok", "provider": _provider.name}


@app.post("/v1/parse", response_model=ParseResponse)
async def parse(request: ParseRequest, caller: str = Depends(_authorize)) -> ParseResponse:
    _rate_limit(caller)
    try:
        parsed = await _provider.parse(request)
    except Exception:
        # Deliberately bare: the client treats any failure as "no suggestion" and falls back to
        # the regex templates, so an upstream outage degrades instead of breaking capture.
        # logging.exception would be safe here (no body), but the traceback can carry the prompt.
        log.error("parse failed: provider=%s", _provider.name)
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "Upstream parse failed")

    log.info(
        "parse ok len=%d brands=%d free_text=%s hit=%s",
        len(request.text),
        len(request.known_brands),
        request.free_text,
        parsed.amount_minor is not None,
    )
    return ParseResponse(**parsed.model_dump(), model=getattr(_provider, "model", _provider.name))
