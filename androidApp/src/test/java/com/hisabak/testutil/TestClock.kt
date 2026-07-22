package com.hisabak.testutil

import com.hisabak.core.common.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A [Clock] with a fixed, mutable instant. Time only changes when a test sets [now], keeping
 *  every time-dependent assertion deterministic. Defaults to UTC to avoid host-zone flakiness. */
class TestClock(
    var now: Instant = Instant.parse("2026-06-17T10:00:00Z"),
    private val zone: ZoneId = ZoneOffset.UTC,
) : Clock {
    override fun now(): Instant = now
    override fun today(zone: ZoneId): LocalDate = now.atZone(this.zone).toLocalDate()
}
