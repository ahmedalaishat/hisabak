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
    base = dict(
        brand="CARREFOUR MALL", brand_text="CARREFOUR MALL",
        amount_minor=12550, amount_text="125.50", currency="AED", date_iso=None,
    )
    return ParsedSms(**{**base, **kw})


def test_parses_a_bank_message(client):
    provider = StubProvider(result=_parsed())
    body = client(provider).post("/v1/parse", json={"text": SMS}, headers=TOKEN)

    assert body.status_code == 200
    assert body.json() == {
        "brand": "CARREFOUR MALL",
        "brand_text": "CARREFOUR MALL",
        "amount_minor": 12550,
        "amount_text": "125.50",
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
    provider = StubProvider(result=ParsedSms(brand=None, brand_text=None, amount_minor=None, amount_text=None, currency=None, date_iso=None))
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


# ── /v1/insights ──────────────────────────────────────────────────────────────

from app.schema import InsightsRequest, Narrative, NarrativeItem  # noqa: E402

SUMMARY = {
    "period": "CURRENT_MONTH",
    "currency": "AED",
    "language": "en",
    "income_minor": 1_250_000,
    "expense_minor": 824_010,
    "prior_income_minor": 1_250_000,
    "prior_expense_minor": 690_000,
    "categories": [
        {"id": "dining", "name": "Dining", "spent_minor": 180_000, "prior_minor": 120_000, "limit_minor": 150_000},
        {"id": "fuel", "name": "Fuel", "spent_minor": 40_000, "prior_minor": 42_000, "limit_minor": None},
    ],
    "uncategorized_minor": 34_000,
    "uncategorized_count": 3,
}


class StubInsightsProvider:
    name = "stub"
    model = "stub-1"

    def __init__(self, result=None, error=None):
        self.result, self.error, self.seen = result, error, []

    async def narrate(self, request: InsightsRequest) -> Narrative:
        self.seen.append(request)
        if self.error:
            raise self.error
        return self.result


@pytest.fixture
def insights_client(monkeypatch):
    def _make(provider):
        monkeypatch.setattr(main, "_insights_provider", provider)
        main._limiter = main.Limiter(
            per_minute=main.RATE_LIMIT_PER_MINUTE, per_ip_daily=1000, global_daily=10_000
        )
        return TestClient(main.app)

    return _make


def _narrative():
    return Narrative(
        items=[
            NarrativeItem(
                category_id="dining",
                headline="Dining is over its limit by 300",
                detail="Three weekend orders pushed it past 1,500. A 1,600 cap would hold next month.",
                suggested_limit_minor=160_000,
            ),
            NarrativeItem(
                category_id=None,
                headline="You saved 34% of your income",
                detail="Down from 45% last month, mostly on dining.",
                suggested_limit_minor=None,
            ),
        ]
    )


def test_narrates_a_summary(insights_client):
    provider = StubInsightsProvider(result=_narrative())
    body = insights_client(provider).post("/v1/insights", json=SUMMARY, headers=TOKEN)

    assert body.status_code == 200
    items = body.json()["items"]
    assert [i["category_id"] for i in items] == ["dining", None]
    assert items[0]["suggested_limit_minor"] == 160_000
    assert body.json()["model"] == "stub-1"
    assert provider.seen[0].language == "en"


def test_the_summary_has_no_field_for_rows_notes_or_text(insights_client):
    # The privacy boundary is the schema. An extra field is silently dropped, never forwarded —
    # so even a buggy client cannot leak a note through this endpoint.
    provider = StubInsightsProvider(result=_narrative())
    body = insights_client(provider).post(
        "/v1/insights", json={**SUMMARY, "notes": ["private"], "text": "SMS body"}, headers=TOKEN
    )

    assert body.status_code == 200
    assert not hasattr(provider.seen[0], "notes")
    assert not hasattr(provider.seen[0], "text")


def test_insights_bounds_are_enforced(insights_client):
    c = insights_client(StubInsightsProvider(result=_narrative()))
    too_many = c.post(
        "/v1/insights",
        json={**SUMMARY, "categories": [SUMMARY["categories"][0]] * 61},
        headers=TOKEN,
    )
    long_name = c.post(
        "/v1/insights",
        json={**SUMMARY, "categories": [{**SUMMARY["categories"][0], "name": "x" * 61}]},
        headers=TOKEN,
    )
    bad_language = c.post("/v1/insights", json={**SUMMARY, "language": "fr"}, headers=TOKEN)
    negative = c.post("/v1/insights", json={**SUMMARY, "expense_minor": -1}, headers=TOKEN)

    assert (too_many.status_code, long_name.status_code, bad_language.status_code, negative.status_code) == (
        422, 422, 422, 422,
    )


def test_insights_requires_the_token_and_shares_the_limiter(insights_client):
    c = insights_client(StubInsightsProvider(result=_narrative()))
    assert c.post("/v1/insights", json=SUMMARY).status_code == 401

    codes = [c.post("/v1/insights", json=SUMMARY, headers=TOKEN).status_code for _ in range(5)]
    assert codes.count(200) == 3
    assert codes.count(429) == 2


def test_insights_provider_failure_degrades_to_502(insights_client):
    c = insights_client(StubInsightsProvider(error=RuntimeError("upstream down")))
    assert c.post("/v1/insights", json=SUMMARY, headers=TOKEN).status_code == 502


def test_insights_prompt_carries_only_figures():
    from app.prompts import build_insights

    system, user = build_insights(InsightsRequest(**SUMMARY))

    assert "dining | Dining | 1,800.00 | 1,200.00 | 1,500.00" in user
    assert "fuel | Fuel | 400.00 | 420.00 | -" in user
    assert "Period: this month (prior period: last month)" in user
    assert "Write in English" in system
    assert "Write in Arabic" in build_insights(InsightsRequest(**{**SUMMARY, "language": "ar"}))[0]
