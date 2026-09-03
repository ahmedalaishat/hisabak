"""Hisabak parse service.

Two endpoints. `/v1/parse`: text in, structured transaction fields out. It exists so devices
without an on-device model (most Android phones, every pre-A17 iPhone) get the same confirm-first
parsing the flagships get — and so the app can learn a regex template from the result and stop
calling this service for that bank format entirely. `/v1/insights`: an aggregate summary of a
period in, a handful of plain-language review items out — the opt-in narrative layer over the
app's deterministic review.

**Message text and figures are never logged or persisted.** The app's privacy policy promises
SMS content and financial figures are only sent when the user opts in and are not retained; that
promise is kept here, in code: request bodies are excluded from access logs and no handler
writes `text`, a category name, or an amount anywhere.
"""

import logging
import os
import secrets
import time

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.providers.anthropic_provider import AnthropicProvider
from app.limits import Limiter, RateLimitExceeded
from app.providers.base import InsightsProvider, ParseProvider
from app.schema import InsightsRequest, InsightsResponse, ParseRequest, ParseResponse

# uvicorn configures its own loggers and leaves everything else silent, so without this the
# operational lines below are dropped. Kept to a single stream handler: no file, no rotation,
# nothing that could persist a request body by accident.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("hisabak")

API_TOKEN = os.getenv("HISABAK_API_TOKEN", "")
# Accepted alongside the current one so a rotation has an overlap window. The token is compiled
# into the apps, so without this every installed build 401s the instant the server rotates —
# which in practice means the token never gets rotated at all.
API_TOKEN_PREVIOUS = os.getenv("HISABAK_API_TOKEN_PREVIOUS", "")
RATE_LIMIT_PER_MINUTE = int(os.getenv("HISABAK_RATE_LIMIT_PER_MINUTE", "30"))
# The ceiling that actually bounds the bill. At roughly $0.0006 a call, 2000/day is about
# $1.20/day worst case — orders of magnitude above real use, and a known number either way.
DAILY_BUDGET = int(os.getenv("HISABAK_DAILY_BUDGET", "2000"))
# Stops a slow drip that never trips the per-minute window.
DAILY_PER_IP = int(os.getenv("HISABAK_DAILY_PER_IP", "200"))

app = FastAPI(title="Hisabak parse service", docs_url=None, redoc_url=None)
_bearer = HTTPBearer(auto_error=False)
_provider: ParseProvider = AnthropicProvider()
# The same object today; two names so either side can be swapped for a different host alone.
_insights_provider: InsightsProvider = _provider
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
    if credentials is None:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Unauthorized")

    # compare_digest, not ==: a short-circuiting comparison leaks the token prefix through
    # response timing, and this token is the only thing protecting the endpoint. Both branches
    # are evaluated so the reply time does not reveal which token matched either.
    matches_current = secrets.compare_digest(credentials.credentials, API_TOKEN)
    matches_previous = bool(API_TOKEN_PREVIOUS) and secrets.compare_digest(
        credentials.credentials, API_TOKEN_PREVIOUS
    )
    if not (matches_current or matches_previous):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Unauthorized")
    if matches_previous and not matches_current:
        # Watch for this to go quiet: it means every install has picked up the new token and the
        # previous one can be dropped from the environment.
        log.info("auth via previous token")
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
    except Exception as exc:
        # Caught broadly: the client treats any failure as "no suggestion" and falls back to the
        # regex templates, so an upstream outage degrades instead of breaking capture.
        #
        # The provider's own message is logged because without it every failure looks identical —
        # a dead key, a malformed request, and a network blip are all just "parse failed", which
        # makes this service impossible to operate. A traceback is still not logged: it can carry
        # the prompt, and the prompt carries the message text.
        detail = getattr(exc, "message", None) or str(exc)
        log.error(
            "parse failed: provider=%s type=%s status=%s detail=%s",
            _provider.name,
            type(exc).__name__,
            getattr(exc, "status_code", "-"),
            detail[:300],
        )
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "Upstream parse failed") from exc

    log.info(
        "parse ok len=%d brands=%d free_text=%s hit=%s",
        len(request.text),
        len(request.known_brands),
        request.free_text,
        parsed.amount_minor is not None,
    )
    return ParseResponse(**parsed.model_dump(), model=getattr(_provider, "model", _provider.name))


@app.post("/v1/insights", response_model=InsightsResponse)
async def insights(request: InsightsRequest, caller: str = Depends(_authorize)) -> InsightsResponse:
    """Narrates a period from aggregates. Shares the parse limiter: one budget bounds the bill.

    Cost stays bounded because the client sends only on the user's tap and answers a repeat tap
    for unchanged figures from its own cache.
    """
    _rate_limit(caller)
    try:
        narrative = await _insights_provider.narrate(request)
    except Exception as exc:
        # Same broad catch as parse, same reasoning: the client falls back to its deterministic
        # review, and a traceback could carry the prompt - which carries the user's figures.
        detail = getattr(exc, "message", None) or str(exc)
        log.error(
            "insights failed: provider=%s type=%s status=%s detail=%s",
            _insights_provider.name,
            type(exc).__name__,
            getattr(exc, "status_code", "-"),
            detail[:300],
        )
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "Upstream narration failed") from exc

    # Counts only: a category name or an amount in the log would be the user's finances at rest.
    log.info(
        "insights ok period=%s lang=%s categories=%d items=%d",
        request.period,
        request.language,
        len(request.categories),
        len(narrative.items),
    )
    return InsightsResponse(
        items=narrative.items,
        model=getattr(_insights_provider, "model", _insights_provider.name),
    )
