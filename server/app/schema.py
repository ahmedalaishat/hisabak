"""Wire contract shared with the Kotlin client (`RemoteAiSmsParser`).

Field names and semantics mirror `AiParsedSms` in commonMain so the client is a thin mapping:
amounts in **minor units**, currency as an ISO-4217 code or null, date as an ISO-8601 string.
Every field is nullable — "this isn't a transaction" is a valid answer, and the client's shared
`sanitize` step is what decides whether a partial result is usable.
"""

from pydantic import BaseModel, Field


class ParseRequest(BaseModel):
    text: str = Field(min_length=1, max_length=2000)
    # The user's existing brand names, most-used first. The model is told to reuse one verbatim
    # when it recognizes the merchant, which is what makes suggestions land on the right brand.
    known_brands: list[str] = Field(default_factory=list, max_length=50)
    # "2026-09-02, Wednesday" — only sent for free text, where "yesterday" has to resolve.
    today_iso: str | None = None
    # Free text is a typed note; otherwise the text is a bank alert, where inventing a date is wrong.
    free_text: bool = False


class ParsedSms(BaseModel):
    """What the model is constrained to return. Nulls are expected, not failures."""

    brand: str | None = Field(description="Merchant name, cleaned. Null if there isn't one.")
    amount_minor: int | None = Field(description="Amount in minor units (cents). 12.50 -> 1250.")
    currency: str | None = Field(description="ISO-4217 code such as AED. Null if not stated.")
    date_iso: str | None = Field(description="ISO-8601 date or date-time. Null if not stated.")


class ParseResponse(ParsedSms):
    model: str
