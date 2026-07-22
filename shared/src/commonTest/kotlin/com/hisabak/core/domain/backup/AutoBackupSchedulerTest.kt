package com.hisabak.core.domain.backup

import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class AutoBackupSchedulerTest {

    @Test
    fun `interval maps each period`() {
        assertNull(autoBackupInterval(AutoBackupPeriod.NEVER))
        assertEquals(1.days, autoBackupInterval(AutoBackupPeriod.DAILY))
        assertEquals(7.days, autoBackupInterval(AutoBackupPeriod.WEEKLY))
        assertEquals(30.days, autoBackupInterval(AutoBackupPeriod.MONTHLY))
    }

    @Test
    fun `delayUntilHour targets 2am today when it's still before 2am`() {
        val now = Instant.parse("2026-06-24T00:30:00Z")
        assertEquals(90.minutes, delayUntilHour(now, TimeZone.UTC, 2))
    }

    @Test
    fun `delayUntilHour rolls to tomorrow once past 2am`() {
        val now = Instant.parse("2026-06-24T03:00:00Z")
        assertEquals(23.hours, delayUntilHour(now, TimeZone.UTC, 2))
    }

    @Test
    fun `delayUntilHour exactly at the hour rolls a full day`() {
        val now = Instant.parse("2026-06-24T02:00:00Z")
        assertEquals(24.hours, delayUntilHour(now, TimeZone.UTC, 2))
    }

    @Test
    fun `delayUntilHour from midnight is the full bias`() {
        val now = Instant.parse("2026-06-24T00:00:00Z")
        assertEquals(2.hours, delayUntilHour(now, TimeZone.UTC, 2))
    }

    @Test
    fun `delayUntilHour resolves a spring-forward gap to the shifted hour`() {
        // Berlin jumps 02:00 -> 03:00 on 2026-03-29; the 02:00 target resolves to 03:00 (+02:00).
        val now = Instant.parse("2026-03-29T00:30:00Z") // 01:30 +01:00 local
        assertEquals(30.minutes, delayUntilHour(now, TimeZone.of("Europe/Berlin"), 2))
    }
}
