"""Spend guards.

The bearer token is compiled into a distributed app, so anyone holding the APK can extract it.
Treat it as identifying the client, not authenticating a person: the defence against abuse is not
secrecy but a bounded bill. These limits exist so the worst case is a known number of dollars
rather than an open-ended one.

Three tiers, cheapest failure first:
  per-minute per IP   stops a hot loop
  per-day per IP      stops a slow drip that never trips the per-minute window
  per-day global      the actual ceiling — bounds a distributed spray across many addresses

All state is in-process and resets on restart. That is deliberate: a shared counter would mean a
datastore, and the global daily cap already bounds the damage a restart could let through.
"""

from collections import deque
from datetime import date, datetime, timezone


class RateLimitExceeded(Exception):
    """Which tier tripped — surfaced only in the log, never to the caller."""

    def __init__(self, scope: str) -> None:
        super().__init__(scope)
        self.scope = scope


class Limiter:
    def __init__(
        self,
        per_minute: int,
        per_ip_daily: int,
        global_daily: int,
        max_tracked_callers: int = 10_000,
        window_seconds: float = 60.0,
    ) -> None:
        self.per_minute = per_minute
        self.per_ip_daily = per_ip_daily
        self.global_daily = global_daily
        self.max_tracked_callers = max_tracked_callers
        self.window_seconds = window_seconds
        self._windows: dict[str, deque[float]] = {}
        self._daily: dict[str, int] = {}
        self._global_today = 0
        self._day: date = self._today()

    @staticmethod
    def _today() -> date:
        return datetime.now(timezone.utc).date()

    @property
    def used_today(self) -> int:
        return self._global_today

    def check(self, caller: str, now: float) -> None:
        """Raises [RateLimitExceeded] if any tier is exhausted; otherwise records the call."""
        self._roll_day()

        # Global first: when the budget is gone it is gone for everyone, and checking it first
        # means a spray cannot push per-IP state around on the way to being rejected.
        if self._global_today >= self.global_daily:
            raise RateLimitExceeded("global_daily")
        if self._daily.get(caller, 0) >= self.per_ip_daily:
            raise RateLimitExceeded("ip_daily")

        self._evict_stale(now)
        if caller not in self._windows and len(self._windows) >= self.max_tracked_callers:
            # Every tracked caller is inside its window: this is a spray across many addresses.
            # Shedding bounds the limiter's own memory and cannot run up a bill.
            raise RateLimitExceeded("tracked_callers")

        window = self._windows.setdefault(caller, deque())
        while window and now - window[0] > self.window_seconds:
            window.popleft()
        if len(window) >= self.per_minute:
            raise RateLimitExceeded("per_minute")

        window.append(now)
        self._daily[caller] = self._daily.get(caller, 0) + 1
        self._global_today += 1

    def _roll_day(self) -> None:
        today = self._today()
        if today != self._day:
            self._day = today
            self._daily.clear()
            self._windows.clear()  # a day-old window can hold nothing current
            self._global_today = 0

    def _evict_stale(self, now: float) -> None:
        stale = [k for k, w in self._windows.items() if not w or now - w[-1] > self.window_seconds]
        for key in stale:
            del self._windows[key]
