"""Endpoint behaviour with a stubbed provider — no network, no API key, no spend.

The model call itself is deliberately not covered: what matters here is that the contract, auth,
rate limiting, and failure handling behave, since those are what the app depends on.
"""

import os

os.environ.setdefault("HISABAK_API_TOKEN", "test-token")
os.environ.setdefault("HISABAK_RATE_LIMIT_PER_MINUTE", "3")

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app import main
from app.schema import ParsedSms, ParseRequest

TOKEN = {"Authorization": "Bearer test-token"}
SMS = "Purchase of AED 125.50 with card 1234 at CARREFOUR MALL completed"


class StubProvider:
    name = "stub"
    model = "stub-1"

    def __init__(self, result=None, error=None):
        self.result, self.error, self.seen = result, error, []

    async def parse(self, request: ParseRequest) -> ParsedSms:
        self.seen.append(request)
        if self.error:
            raise self.error
        return self.result


@pytest.fixture
def client(monkeypatch):
    def _make(provider):
        monkeypatch.setattr(main, "_provider", provider)
        main._hits.clear()
        return TestClient(main.app)

    return _make


def _parsed(**kw):
    base = dict(brand="CARREFOUR MALL", amount_minor=12550, currency="AED", date_iso=None)
    return ParsedSms(**{**base, **kw})


def test_parses_a_bank_message(client):
    provider = StubProvider(result=_parsed())
    body = client(provider).post("/v1/parse", json={"text": SMS}, headers=TOKEN)

    assert body.status_code == 200
    assert body.json() == {
        "brand": "CARREFOUR MALL",
        "amount_minor": 12550,
        "currency": "AED",
        "date_iso": None,
        "model": "stub-1",
    }


def test_known_brands_and_free_text_reach_the_provider(client):
    provider = StubProvider(result=_parsed())
    client(provider).post(
        "/v1/parse",
        json={
            "text": "lunch 45 yesterday",
            "known_brands": ["Noon", "Talabat"],
            "today_iso": "2026-09-02, Wednesday",
            "free_text": True,
        },
        headers=TOKEN,
    )

    seen = provider.seen[0]
    assert seen.known_brands == ["Noon", "Talabat"]
    assert seen.free_text is True
    assert seen.today_iso == "2026-09-02, Wednesday"


def test_a_non_transaction_returns_nulls_not_an_error(client):
    provider = StubProvider(result=ParsedSms(brand=None, amount_minor=None, currency=None, date_iso=None))
    body = client(provider).post("/v1/parse", json={"text": "hi mum"}, headers=TOKEN)

    # The client treats this as "no suggestion"; an error status would look like an outage.
    assert body.status_code == 200
    assert body.json()["amount_minor"] is None


def test_unauthenticated_requests_are_rejected(client):
    c = client(StubProvider(result=_parsed()))
    assert c.post("/v1/parse", json={"text": SMS}).status_code == 401
    assert c.post("/v1/parse", json={"text": SMS}, headers={"Authorization": "Bearer wrong"}).status_code == 401


def test_oversized_and_empty_text_are_rejected(client):
    c = client(StubProvider(result=_parsed()))
    assert c.post("/v1/parse", json={"text": ""}, headers=TOKEN).status_code == 422
    assert c.post("/v1/parse", json={"text": "x" * 2001}, headers=TOKEN).status_code == 422


def test_rate_limit_caps_a_runaway_client(client):
    c = client(StubProvider(result=_parsed()))
    codes = [c.post("/v1/parse", json={"text": SMS}, headers=TOKEN).status_code for _ in range(5)]

    # A runaway client must not be able to run up an API bill.
    assert codes.count(200) == 3
    assert codes.count(429) == 2


def test_provider_failure_degrades_to_502(client):
    c = client(StubProvider(error=RuntimeError("upstream down")))
    assert c.post("/v1/parse", json={"text": SMS}, headers=TOKEN).status_code == 502


def test_oversized_brand_fields_are_rejected(client):
    c = client(StubProvider(result=_parsed()))

    # Every brand is interpolated into the prompt, so an unbounded field is an unbounded bill.
    too_many = c.post(
        "/v1/parse", json={"text": SMS, "known_brands": ["b"] * 51}, headers=TOKEN
    )
    too_long = c.post(
        "/v1/parse", json={"text": SMS, "known_brands": ["x" * 121]}, headers=TOKEN
    )
    long_today = c.post(
        "/v1/parse", json={"text": SMS, "today_iso": "y" * 65}, headers=TOKEN
    )

    assert (too_many.status_code, too_long.status_code, long_today.status_code) == (422, 422, 422)


def test_rate_limiter_state_does_not_grow_without_bound(monkeypatch):
    # Exercised directly: the limiter keys on source address, so a spray across many addresses
    # is what grows it. Driving that through the TestClient is not possible - every request
    # there reports the same client host.
    main._hits.clear()
    monkeypatch.setattr(main, "_MAX_TRACKED_CALLERS", 3)
    monkeypatch.setattr(main, "RATE_LIMIT_PER_MINUTE", 100)

    shed = 0
    for i in range(50):
        try:
            main._rate_limit(f"ip-{i}")
        except HTTPException as e:
            assert e.status_code == 429
            shed += 1

    assert len(main._hits) <= 3
    assert shed > 0, "a spray across many addresses must be shed, not accumulated"


def test_stale_rate_limit_windows_are_reclaimed(monkeypatch):
    main._hits.clear()
    clock = [1000.0]
    monkeypatch.setattr(main.time, "monotonic", lambda: clock[0])

    main._rate_limit("ip-a")
    assert set(main._hits) == {"ip-a"}

    # Past the window: the next caller reclaims the previous entry rather than adding to it.
    clock[0] += main._WINDOW_SECONDS + 1
    main._rate_limit("ip-b")

    assert set(main._hits) == {"ip-b"}


def test_token_comparison_is_constant_time(client):
    # A short-circuiting == leaks the token prefix through response timing.
    import inspect

    source = inspect.getsource(main._authorize)
    assert "compare_digest" in source
    assert "!= API_TOKEN" not in source


def test_health_needs_no_token(client):
    assert client(StubProvider(result=_parsed())).get("/health").json()["status"] == "ok"
