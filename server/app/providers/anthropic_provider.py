"""Anthropic-backed extraction.

Model is env-configurable and defaults to Haiku 4.5: the task is short structured extraction, and
because the app learns a regex template from each successful parse, this is called roughly once per
bank *format* rather than once per message — so capability per call matters far less than it would
in a chat workload. Set HISABAK_MODEL to move up.

The system prompt is cached: it is identical for every request with the same brand list, and
cache reads bill at ~0.1x.
"""

import os

import anthropic

from app.prompts import build
from app.schema import ParsedSms, ParseRequest

DEFAULT_MODEL = "claude-haiku-4-5"


class AnthropicProvider:
    name = "anthropic"

    def __init__(self) -> None:
        # Credentials resolve from ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN / an `ant auth` profile.
        self._client = anthropic.AsyncAnthropic(timeout=20.0, max_retries=2)
        self._model = os.getenv("HISABAK_MODEL", DEFAULT_MODEL)

    @property
    def model(self) -> str:
        return self._model

    async def parse(self, request: ParseRequest) -> ParsedSms:
        response = await self._client.messages.parse(
            model=self._model,
            max_tokens=256,
            system=[
                {
                    "type": "text",
                    "text": build(
                        free_text=request.free_text,
                        today=request.today_iso,
                        known_brands=request.known_brands,
                    ),
                    "cache_control": {"type": "ephemeral"},
                }
            ],
            messages=[{"role": "user", "content": request.text}],
            output_format=ParsedSms,
        )
        # Structured output guarantees the shape; a refusal or an empty turn does not.
        return response.parsed_output or ParsedSms(
            brand=None, amount_minor=None, currency=None, date_iso=None
        )
