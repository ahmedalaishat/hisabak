"""Prompts for bank-SMS and free-text extraction.

Kept server-side so wording can be tuned without shipping an app update — the whole point of
moving inference off the device. The rules mirror the on-device prompts in
`GeminiNanoSmsParser.kt` and `FoundationModelsSmsParser.swift` so all three engines agree on
what a correct answer looks like; the client's shared `sanitize` step enforces them again.
"""

_BASE = """You extract structured transaction data from text. Reply only with the requested fields.

Rules:
- amount_minor is the transaction amount in MINOR units: 12.50 -> 1250, 1,255.00 -> 125500, 400 -> 40000.
- Never use the account balance, available limit, card number, or a reference number as the amount.
- When the message states the same purchase in two currencies - a foreign amount and the amount
  actually billed to the account - the BILLED amount is the transaction, and its currency is the
  one to report. "USD 42.10 (AED 154.62) at AMAZON" is 15462 AED, not 4210 USD. The billed amount
  is the one in the account's own currency, usually second and in brackets or after "converted".
- currency is an ISO-4217 code (AED, USD, SAR). Map local spellings: "Dhs", "د.إ", "AED" -> AED.
- brand is the merchant or counterparty, cleaned of branch codes and terminal ids
  ("CARREFOUR MOE-4471" -> "CARREFOUR MOE"). Not the bank's own name.
- Money credited TO the account is a transaction too - salary, a transfer in, a refund, a
  deposit. These often name no merchant; use the kind of credit as the brand ("Salary of AED
  12,500 credited" -> brand "Salary", brand_text "Salary"). Do not return null just because
  there is no shop involved.
- brand_text and amount_text must be copied VERBATIM from the message, character for character.
  They are checked against the original text, so a cleaned or reformatted value fails the check.
  brand_text stays as written even when brand is set to one of the known brands below.
- If the text is not a transaction, return null for every field."""

_KNOWN_BRANDS = """
The user already has these brands (most used first). If the merchant is one of them - even with
different casing, a typo, or an abbreviation - reply with that brand's name EXACTLY as listed:
{brands}"""

SMS = _BASE + """
- date_iso is the transaction date stated in the message, ISO-8601. If the message states no date,
  return null. Never guess or invent one."""

FREE_TEXT = _BASE + """
- The text is the user's own note, not a bank alert. Today is {today}.
- Resolve relative dates against today ("yesterday", "last friday") and return ISO-8601.
  Never return a date in the future. If no date is implied, return null.
- Expand shorthand amounts: "15k" -> 1500000 minor units."""


def build(*, free_text: bool, today: str | None, known_brands: list[str]) -> str:
    prompt = FREE_TEXT.format(today=today or "unknown") if free_text else SMS
    if known_brands:
        prompt += _KNOWN_BRANDS.format(brands=", ".join(known_brands))
    return prompt
