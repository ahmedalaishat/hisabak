package com.hisabak.core.common

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.LocalDate

class DateRangeTest {

    @Test
    fun `totalDays counts calendar days across a year boundary`() {
        val range = DateRange(LocalDate(2025, 12, 30), LocalDate(2026, 1, 2))
        assertEquals(4L, range.totalDays)
    }

    @Test
    fun `totalDays across a DST-change month stays calendar-based`() {
        // 2026-03-29 is the EU spring-forward day; date arithmetic must not shift by wall-clock hours.
        val range = DateRange(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
        assertEquals(31L, range.totalDays)
    }

    @Test
    fun `single-day range is one day`() {
        val day = LocalDate(2026, 6, 17)
        assertEquals(1L, DateRange(day, day).totalDays)
    }

    @Test
    fun `month range covers leap-year february`() {
        val range = DateRange.month(LocalDate(2024, 2, 15))
        assertEquals(LocalDate(2024, 2, 1), range.start)
        assertEquals(LocalDate(2024, 2, 29), range.endInclusive)
        assertEquals(29L, range.totalDays)
    }

    @Test
    fun `month range covers non-leap february`() {
        val range = DateRange.month(LocalDate(2026, 2, 1))
        assertEquals(LocalDate(2026, 2, 28), range.endInclusive)
        assertEquals(28L, range.totalDays)
    }

    @Test
    fun `contains is inclusive of both endpoints`() {
        val range = DateRange(LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
        assertTrue(LocalDate(2026, 6, 1) in range)
        assertTrue(LocalDate(2026, 6, 30) in range)
        assertFalse(LocalDate(2026, 5, 31) in range)
        assertFalse(LocalDate(2026, 7, 1) in range)
    }
}
