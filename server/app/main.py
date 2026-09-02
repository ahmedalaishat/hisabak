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
from collections import defaultdict, deque

from fastapi import Depends, FastAPI, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.providers.anthropic_provider import AnthropicProvider
from app.providers.base import ParseProvider
from app.schema import ParseRequest, ParseResponse

# uvicorn configures its own loggers and leaves everything else silent, so without this the
# operational lines below are dropped. Kept to a single stream handler: no file, no rotation,
# nothing that could persist a request body by accident.
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("hisabak")

API_TOKEN = os.getenv("HISABAK_API_TOKEN", "")
RATE_LIMIT_PER_MINUTE = int(os.getenv("HISABAK_RATE_LIMIT_PER_MINUTE", "30"))

app = FastAPI(title="Hisabak parse service", docs_url=None, redoc_url=None)
_bearer = HTTPBearer(auto_error=False)
_provider: ParseProvider = AnthropicProvider()
_hits: dict[str, deque[float]] = defaultdict(deque)
_WINDOW_SECONDS = 60.0
# Bounds the limiter's own memory; ~10k entries is a few MB and far above real usage.
_MAX_TRACKED_CALLERS = 10_000


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
    """Per-caller sliding window. A runaway client must not be able to run up an API bill.

    The window map is pruned on every call and hard-capped: it is keyed by source address, so
    without that, traffic from many addresses grows it without bound — the limiter would itself
    become the exhaustion vector it exists to prevent.
    """
    now = time.monotonic()

    for key in [k for k, w in _hits.items() if not w or now - w[-1] > _WINDOW_SECONDS]:
        del _hits[key]
    if caller not in _hits and len(_hits) >= _MAX_TRACKED_CALLERS:
        # Every tracked caller is inside its window, so this is a spray across many addresses.
        # Shedding is the safe failure: it bounds memory and cannot run up an API bill.
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Rate limit exceeded")

    window = _hits[caller]
    while window and now - window[0] > _WINDOW_SECONDS:
        window.popleft()
    if len(window) >= RATE_LIMIT_PER_MINUTE:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Rate limit exceeded")
    window.append(now)


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
