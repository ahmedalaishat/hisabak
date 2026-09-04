"""Wire contract shared with the Kotlin client (`RemoteAiSmsParser`, `RemoteAiInsights`).

Field names and semantics mirror the commonMain types so the client is a thin mapping:
amounts in **minor units**, currency as an ISO-4217 code or null, date as an ISO-8601 string.
Every parse field is nullable — "this isn't a transaction" is a valid answer, and the client's
shared `sanitize` step is what decides whether a partial result is usable.
"""

import re
from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints, field_validator

# Mirrors SuggestAiParseUseCase.MAX_KNOWN_BRANDS; a longer list is a client bug, not a bigger prompt.
MAX_KNOWN_BRANDS = 50
BrandName = Annotated[str, StringConstraints(min_length=1, max_length=120)]

# A bank alert or a spending note always states an amount, and neither runs long. Holding the
# endpoint to that shape costs no real request anything and makes it near-useless as a free
# general-purpose model — the token is compiled into a distributed app and cannot be kept secret,
# so narrowing what the endpoint will even accept is worth more than guarding the token.
MAX_TEXT = 800
_HAS_DIGIT = re.compile(r"[0-9\u0660-\u0669\u06F0-\u06F9]")


class ParseRequest(BaseModel):
    text: str = Field(min_length=1, max_length=MAX_TEXT)
    # The user's existing brand names, most-used first. The model is told to reuse one verbatim
    # when it recognizes the merchant, which is what makes suggestions land on the right brand.
    #
    # Both the count and each name are bounded: everything here is interpolated into the prompt,
    # so an unbounded field is an unbounded token bill as well as unbounded memory.
    known_brands: list[BrandName] = Field(
        default_factory=list,
        max_length=MAX_KNOWN_BRANDS,
    )
    # "2026-09-02, Wednesday" — only sent for free text, where "yesterday" has to resolve.
    today_iso: str | None = Field(default=None, max_length=64)
    # Free text is a typed note; otherwise the text is a bank alert, where inventing a date is wrong.
    free_text: bool = False

    @field_validator("text")
    @classmethod
    def _must_state_an_amount(cls, value: str) -> str:
        # Arabic-Indic and extended Arabic-Indic digits count: Arabic bank SMS use them.
        if not _HAS_DIGIT.search(value):
            raise ValueError("text must contain a number")
        return value


class ParsedSms(BaseModel):
    """What the model is constrained to return. Nulls are expected, not failures.

    The `*_text` fields are **evidence**, not decoration: they let the client verify the parse
    against the original message instead of trusting it, and they tell template synthesis exactly
    which characters to replace with a placeholder. A self-reported confidence score would be
    worse — models are poorly calibrated and cluster near 0.9 whether right or wrong, whereas
    "this substring is in the message" is checkable.
    """

    brand: str | None = Field(description="Merchant name, cleaned. Null if there isn't one.")
    brand_text: str | None = Field(
        description=(
            "The merchant EXACTLY as it appears in the message, copied character for character, "
            "even when `brand` was set to a different known brand name. For "
            "'at TALABAT-DXB-991' this is 'TALABAT-DXB-991'. Null if there is no merchant."
        )
    )
    amount_minor: int | None = Field(description="Amount in minor units (cents). 12.50 -> 1250.")
    amount_text: str | None = Field(
        description=(
            "The amount EXACTLY as written in the message, without the currency: '62.00', "
            "'1,255.00', '400'. Null if there is no amount."
        )
    )
    currency: str | None = Field(description="ISO-4217 code such as AED. Null if not stated.")
    date_iso: str | None = Field(description="ISO-8601 date or date-time. Null if not stated.")


class ParseResponse(ParsedSms):
    model: str


# ── Insights ──────────────────────────────────────────────────────────────────

# Mirrors the client's cap when it builds the request. A ledger with more expense categories than
# this sends its largest; the rest are immaterial to a review and every row is prompt tokens.
MAX_CATEGORIES = 60
CategoryName = Annotated[str, StringConstraints(min_length=1, max_length=60)]
Minor = Annotated[int, Field(ge=0, le=10**13)]


class CategoryFigures(BaseModel):
    id: str = Field(min_length=1, max_length=64)
    name: CategoryName
    spent_minor: Minor
    prior_minor: Minor | None = None
    limit_minor: Minor | None = None


class InsightsRequest(BaseModel):
    """The `InsightsSummary` from the phone: aggregates only. This shape **is** the privacy boundary
    — there is no field for a transaction, a note, a brand, or a message, so none can arrive."""

    period: str = Field(min_length=1, max_length=32)
    currency: str = Field(min_length=1, max_length=8)
    language: Literal["en", "ar"] = "en"
    income_minor: Minor
    expense_minor: Minor
    prior_income_minor: Minor | None = None
    prior_expense_minor: Minor | None = None
    categories: list[CategoryFigures] = Field(default_factory=list, max_length=MAX_CATEGORIES)
    uncategorized_minor: Minor = 0
    uncategorized_count: int = Field(default=0, ge=0, le=100_000)


class NarrativeItem(BaseModel):
    category_id: str | None = Field(
        description=(
            "The id of the category this is about, exactly as listed in the figures. Null when "
            "the item is about the period as a whole (savings, uncategorized spend)."
        )
    )
    headline: str = Field(description="One plain sentence stating what happened. At most 60 characters.")
    detail: str = Field(
        description="Why it matters and what to do, addressed to the reader as 'you'. At most 200 characters."
    )
    suggested_limit_minor: int | None = Field(
        description=(
            "A monthly spending cap to propose for this category, in minor units, or null. Only "
            "for an expense category that has no limit or is over its limit."
        )
    )


class Narrative(BaseModel):
    items: list[NarrativeItem] = Field(description="Two to five items, most important first.")


class InsightsResponse(Narrative):
    model: str


# ── Ask ───────────────────────────────────────────────────────────────────────

# The one free-text field the service accepts. Short by design: a question about one's own
# spending fits, an essay request does not, and every character is prompt tokens.
MAX_QUESTION = 500
MAX_HISTORY_TURNS = 6
MAX_TURN_TEXT = 1200


class AskTurn(BaseModel):
    role: Literal["user", "assistant"]
    text: str = Field(min_length=1, max_length=MAX_TURN_TEXT)


class AskRequest(BaseModel):
    """A question about the same summary the narrative uses — the summary *is* the whole context,
    so nothing beyond it (no rows, no notes) can be asked about, let alone answered."""

    summary: InsightsRequest
    question: str = Field(min_length=1, max_length=MAX_QUESTION)
    # The last few turns of this conversation, oldest first, so a follow-up ("and fuel?") resolves.
    history: list[AskTurn] = Field(default_factory=list, max_length=MAX_HISTORY_TURNS)


class AskAnswer(BaseModel):
    answer: str = Field(
        description="The reply, in the requested language. Plain text, at most 120 words, no markdown."
    )
    on_topic: bool = Field(
        description=(
            "True when the question was about this person's finances as given in the figures. "
            "False when it was something else and the reply is a brief refusal."
        )
    )


class AskResponse(AskAnswer):
    model: str
