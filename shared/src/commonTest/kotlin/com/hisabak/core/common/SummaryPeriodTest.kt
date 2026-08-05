package com.hisabak.core.common

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class SummaryPeriodTest {

    @Test
    fun `current month window from Jan 31 spans Jan 1 to Feb 1`() {
        val range = SummaryPeriod.CURRENT_MONTH.dateRange(LocalDate(2026, 1, 31))
        assertEquals(LocalDate(2026, 1, 1) to LocalDate(2026, 2, 1), range)
    }

    @Test
    fun `last month window from Mar 31 spans all of february`() {
        val range = SummaryPeriod.LAST_MONTH.dateRange(LocalDate(2026, 3, 31))
        assertEquals(LocalDate(2026, 2, 1) to LocalDate(2026, 3, 1), range)
    }

    @Test
    fun `last month window in a leap year keeps Feb 29 inside`() {
        val range = SummaryPeriod.LAST_MONTH.dateRange(LocalDate(2024, 3, 15))
        assertEquals(LocalDate(2024, 2, 1) to LocalDate(2024, 3, 1), range)
    }

    @Test
    fun `last year window from a leap day spans the previous year`() {
        val range = SummaryPeriod.LAST_YEAR.dateRange(LocalDate(2024, 2, 29))
        assertEquals(LocalDate(2023, 1, 1) to LocalDate(2024, 1, 1), range)
    }

    @Test
    fun `all time has no window`() {
        assertNull(SummaryPeriod.ALL.dateRange(LocalDate(2026, 6, 17)))
        assertNull(SummaryPeriod.ALL.instantRange(LocalDate(2026, 6, 17), TimeZone.UTC))
    }

    @Test
    fun `instant range uses local midnights in the given zone`() {
        val (start, end) = SummaryPeriod.CURRENT_MONTH.instantRange(
            LocalDate(2026, 6, 15),
            TimeZone.of("Asia/Dubai"),
        )!!
        assertEquals(Instant.parse("2026-05-31T20:00:00Z"), start)
        assertEquals(Instant.parse("2026-06-30T20:00:00Z"), end)
    }

    @Test
    fun `previous window of current month is the prior calendar month`() {
        val (start, end) = SummaryPeriod.CURRENT_MONTH.previousInstantRange(
            LocalDate(2026, 3, 31),
            TimeZone.UTC,
        )!!
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), start)
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), end)
    }
}
