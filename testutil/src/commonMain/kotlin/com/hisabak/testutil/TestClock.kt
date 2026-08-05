package com.hisabak.testutil

import com.hisabak.core.common.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** A [Clock] with a fixed, mutable instant. Time only changes when a test sets [now], keeping
 *  every time-dependent assertion deterministic. Defaults to UTC to avoid host-zone flakiness. */
class TestClock(
    var now: Instant = Instant.parse("2026-06-17T10:00:00Z"),
    private val zone: TimeZone = TimeZone.UTC,
) : Clock {
    override fun now(): Instant = now
    override fun today(zone: TimeZone): LocalDate = now.toLocalDateTime(this.zone).date
}
