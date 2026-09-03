"""Anthropic-backed extraction and narration.

Model is env-configurable and defaults to Haiku 4.5: the task is short structured extraction, and
because the app learns a regex template from each successful parse, this is called roughly once per
bank *format* rather than once per message — so capability per call matters far less than it would
in a chat workload. Set HISABAK_MODEL to move up.

No prompt caching: the system prompt is ~370 tokens and Haiku 4.5 will not cache a prefix under
4096, so a cache_control marker would be silently inert. It would not pay off here anyway —
parses are minutes or hours apart and the cache TTL is five minutes.
"""

import os

import anthropic

from app.prompts import build, build_ask, build_insights
from app.schema import AskAnswer, AskRequest, InsightsRequest, Narrative, ParsedSms, ParseRequest

DEFAULT_MODEL = "claude-haiku-4-5"


class AnthropicProvider:
    name = "anthropic"

    def __init__(self) -> None:
        # Credentials resolve from ANTHROPIC_API_KEY / ANTHROPIC_AUTH_TOKEN / an `ant auth` profile.
        #
        # An identity-linked key (personal or service account) that is not scoped to a single
        # workspace must name the workspace on every request, or the API rejects it with a 400.
        # A workspace-scoped key needs no header, so this stays unset in that case.
        workspace = os.getenv("ANTHROPIC_WORKSPACE_ID", "").strip()
        self._client = anthropic.AsyncAnthropic(
            timeout=20.0,
            max_retries=2,
            default_headers={"anthropic-workspace-id": workspace} if workspace else None,
        )
        self._model = os.getenv("HISABAK_MODEL", DEFAULT_MODEL)

    @property
    def model(self) -> str:
        return self._model

    async def parse(self, request: ParseRequest) -> ParsedSms:
        response = await self._client.messages.parse(
            model=self._model,
            max_tokens=256,
            system=build(
                free_text=request.free_text,
                today=request.today_iso,
                known_brands=request.known_brands,
            ),
            messages=[{"role": "user", "content": request.text}],
            output_format=ParsedSms,
        )
        # Structured output guarantees the shape; a refusal or an empty turn does not.
        return response.parsed_output or ParsedSms(
            brand=None, brand_text=None, amount_minor=None,
            amount_text=None, currency=None, date_iso=None,
        )

    async def narrate(self, request: InsightsRequest) -> Narrative:
        system, summary = build_insights(request)
        # Larger output than a parse - five short items in Arabic run to a few hundred tokens - but
        # still a hard ceiling, so the cost of a call is a constant.
        response = await self._client.messages.parse(
            model=self._model,
            max_tokens=1024,
            system=system,
            messages=[{"role": "user", "content": summary}],
            output_format=Narrative,
        )
        return response.parsed_output or Narrative(items=[])

    async def ask(self, request: AskRequest) -> AskAnswer:
        system, messages = build_ask(request)
        # 120 words is ~200 tokens; the ceiling leaves room for Arabic and the JSON envelope.
        response = await self._client.messages.parse(
            model=self._model,
            max_tokens=400,
            system=system,
            messages=messages,
            output_format=AskAnswer,
        )
        return response.parsed_output or AskAnswer(answer="", on_topic=False)
