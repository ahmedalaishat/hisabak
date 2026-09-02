"""The seam that keeps the hosting decision out of the app.

A provider turns message text into a [ParsedSms]. Anthropic is the default; a self-hosted model
is a drop-in replacement behind this Protocol, so switching costs an env var and changes nothing
on the client.
"""

from typing import Protocol

from app.schema import ParsedSms, ParseRequest


class ParseProvider(Protocol):
    name: str

    async def parse(self, request: ParseRequest) -> ParsedSms: ...
