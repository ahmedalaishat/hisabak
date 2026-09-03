"""The seam that keeps the hosting decision out of the app.

A provider turns message text into a [ParsedSms], or an aggregate summary into a [Narrative].
Anthropic is the default; a self-hosted model is a drop-in replacement behind these Protocols, so
switching costs an env var and changes nothing on the client.
"""

from typing import Protocol

from app.schema import InsightsRequest, Narrative, ParsedSms, ParseRequest


class ParseProvider(Protocol):
    name: str

    async def parse(self, request: ParseRequest) -> ParsedSms: ...


class InsightsProvider(Protocol):
    name: str

    async def narrate(self, request: InsightsRequest) -> Narrative: ...
