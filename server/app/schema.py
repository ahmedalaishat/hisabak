"""Wire contract shared with the Kotlin client (`RemoteAiSmsParser`).

Field names and semantics mirror `AiParsedSms` in commonMain so the client is a thin mapping:
amounts in **minor units**, currency as an ISO-4217 code or null, date as an ISO-8601 string.
Every field is nullable — "this isn't a transaction" is a valid answer, and the client's shared
`sanitize` step is what decides whether a partial result is usable.
"""

import re
from typing import Annotated

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
