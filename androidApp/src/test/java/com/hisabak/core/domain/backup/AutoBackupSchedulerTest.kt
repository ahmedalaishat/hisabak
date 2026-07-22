package com.hisabak.core.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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
        val now = ZonedDateTime.parse("2026-06-24T00:30:00Z")
        assertEquals(90.minutes, delayUntilHour(now, 2))
    }

    @Test
    fun `delayUntilHour rolls to tomorrow once past 2am`() {
        val now = ZonedDateTime.parse("2026-06-24T03:00:00Z")
        assertEquals(23.hours, delayUntilHour(now, 2))
    }
}
