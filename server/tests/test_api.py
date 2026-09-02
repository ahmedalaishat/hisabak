"""Endpoint behaviour with a stubbed provider — no network, no API key, no spend.

The model call itself is deliberately not covered: what matters here is that the contract, auth,
rate limiting, and failure handling behave, since those are what the app depends on.
"""

import os

os.environ.setdefault("HISABAK_API_TOKEN", "test-token")
os.environ.setdefault("HISABAK_RATE_LIMIT_PER_MINUTE", "3")
os.environ.setdefault("HISABAK_DAILY_BUDGET", "10000")
os.environ.setdefault("HISABAK_DAILY_PER_IP", "1000")

import pytest
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
        main._limiter = main.Limiter(
        per_minute=main.RATE_LIMIT_PER_MINUTE, per_ip_daily=1000, global_daily=10_000
    )
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
    body = client(provider).post(
        "/v1/parse", json={"text": "Hi, are we still on for dinner at 8?"}, headers=TOKEN
    )

    # The client treats this as "no suggestion"; an error status would look like an outage.
    assert body.status_code == 200
    assert body.json()["amount_minor"] is None


def test_text_with_no_number_never_reaches_the_model(client):
    provider = StubProvider(result=_parsed())
    body = client(provider).post(
        "/v1/parse", json={"text": "write me a poem about the sea"}, headers=TOKEN
    )

    # A bank alert or a spending note always states an amount. Rejecting at the boundary blunts
    # the endpoint as a free general-purpose model and costs nothing upstream.
    assert body.status_code == 422
    assert provider.seen == []


def test_arabic_indic_digits_count_as_a_number(client):
    provider = StubProvider(result=_parsed())
    body = client(provider).post("/v1/parse", json={"text": "قهوة ٣٥ درهم امس"}, headers=TOKEN)

    assert body.status_code == 200


def test_the_previous_token_is_accepted_during_a_rotation(client, monkeypatch):
    monkeypatch.setattr(main, "API_TOKEN_PREVIOUS", "old-token")
    c = client(StubProvider(result=_parsed()))

    old = c.post("/v1/parse", json={"text": SMS}, headers={"Authorization": "Bearer old-token"})
    new = c.post("/v1/parse", json={"text": SMS}, headers=TOKEN)

    # Without the overlap, rotating 401s every installed app the instant the server restarts.
    assert (old.status_code, new.status_code) == (200, 200)


def test_a_retired_token_stops_working_once_cleared(client, monkeypatch):
    monkeypatch.setattr(main, "API_TOKEN_PREVIOUS", "")
    c = client(StubProvider(result=_parsed()))

    body = c.post("/v1/parse", json={"text": SMS}, headers={"Authorization": "Bearer old-token"})

    assert body.status_code == 401


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


def test_token_comparison_is_constant_time(client):
    # A short-circuiting == leaks the token prefix through response timing.
    import inspect

    source = inspect.getsource(main._authorize)
    assert "compare_digest" in source
    assert "!= API_TOKEN" not in source


def test_health_needs_no_token(client):
    assert client(StubProvider(result=_parsed())).get("/health").json()["status"] == "ok"
