package com.hisabak.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth

class SummaryPeriodTest {

    private val today = LocalDate(2026, 10, 3)

    @Test
    fun `this month from Jan 31 spans Jan 1 to Feb 1`() {
        val window = SummaryPeriod.thisMonth(LocalDate(2026, 1, 31)).window
        assertEquals(LocalDate(2026, 1, 1) to LocalDate(2026, 2, 1), window)
    }

    @Test
    fun `last month from Mar 31 spans all of february`() {
        val window = SummaryPeriod.lastMonth(LocalDate(2026, 3, 31)).window
        assertEquals(LocalDate(2026, 2, 1) to LocalDate(2026, 3, 1), window)
    }

    @Test
    fun `last month in a leap year keeps Feb 29 inside`() {
        val window = SummaryPeriod.lastMonth(LocalDate(2024, 3, 15)).window
        assertEquals(LocalDate(2024, 2, 1) to LocalDate(2024, 3, 1), window)
    }

    @Test
    fun `last year from a leap day spans the previous year`() {
        val window = SummaryPeriod.lastYear(LocalDate(2024, 2, 29)).window
        assertEquals(LocalDate(2023, 1, 1) to LocalDate(2024, 1, 1), window)
    }

    @Test
    fun `a custom range includes its last day`() {
        val window = SummaryPeriod.Custom(LocalDate(2026, 7, 6), LocalDate(2026, 10, 3)).window
        assertEquals(LocalDate(2026, 7, 6) to LocalDate(2026, 10, 4), window)
    }

    @Test
    fun `all time has no window`() {
        assertNull(SummaryPeriod.All.window)
        assertNull(SummaryPeriod.All.instantRange(TimeZone.UTC))
    }

    @Test
    fun `instant range uses local midnights in the given zone`() {
        val (start, end) = SummaryPeriod.Month(YearMonth(2026, Month.JUNE)).instantRange(TimeZone.of("Asia/Dubai"))!!
        assertEquals(Instant.parse("2026-05-31T20:00:00Z"), start)
        assertEquals(Instant.parse("2026-06-30T20:00:00Z"), end)
    }

    @Test
    fun `the previous month of March is February`() {
        val (start, end) = SummaryPeriod.Month(YearMonth(2026, Month.MARCH)).previousInstantRange(TimeZone.UTC)!!
        assertEquals(Instant.parse("2026-02-01T00:00:00Z"), start)
        assertEquals(Instant.parse("2026-03-01T00:00:00Z"), end)
    }

    @Test
    fun `stepping a month crosses the year boundary`() {
        val january = SummaryPeriod.Month(YearMonth(2026, Month.JANUARY))
        assertEquals(SummaryPeriod.Month(YearMonth(2025, Month.DECEMBER)), january.previous)
        assertEquals(SummaryPeriod.Month(YearMonth(2026, Month.FEBRUARY)), january.next)
    }

    @Test
    fun `a custom range steps by its own length`() {
        val range = SummaryPeriod.lastDays(today, 90)
        assertEquals(SummaryPeriod.Custom(LocalDate(2026, 7, 6), today), range)
        assertEquals(SummaryPeriod.Custom(LocalDate(2026, 4, 7), LocalDate(2026, 7, 5)), range.previous)
        assertEquals(SummaryPeriod.Custom(LocalDate(2026, 10, 4), LocalDate(2027, 1, 1)), range.next)
    }

    @Test
    fun `all time has no neighbours`() {
        assertNull(SummaryPeriod.All.previous)
        assertNull(SummaryPeriod.All.next)
        assertFalse(SummaryPeriod.All.canStepForward(today))
        assertFalse(SummaryPeriod.All.canStepBack(LocalDate(2019, 3, 1)))
    }

    @Test
    fun `the future cannot be stepped into`() {
        assertFalse(SummaryPeriod.thisMonth(today).canStepForward(today))
        assertTrue(SummaryPeriod.lastMonth(today).canStepForward(today))
        assertFalse(SummaryPeriod.thisYear(today).canStepForward(today))
        assertFalse(SummaryPeriod.lastDays(today, 30).canStepForward(today))
    }

    @Test
    fun `stepping back stops at the first activity`() {
        val march = SummaryPeriod.Month(YearMonth(2019, Month.MARCH))
        assertFalse(march.canStepBack(LocalDate(2019, 3, 12)))
        assertTrue(march.canStepBack(LocalDate(2019, 2, 28)))
        assertFalse(march.canStepBack(null))
    }

    @Test
    fun `every period survives an encode and decode`() {
        listOf(
            SummaryPeriod.thisMonth(today),
            SummaryPeriod.Year(2024),
            SummaryPeriod.Custom(LocalDate(2025, 12, 20), LocalDate(2026, 1, 9)),
            SummaryPeriod.All,
        ).forEach { assertEquals(it, SummaryPeriod.decode(it.encode())) }
    }

    @Test
    fun `garbage decodes to null rather than throwing`() {
        assertNull(SummaryPeriod.decode("CURRENT_MONTH"))
        assertNull(SummaryPeriod.decode("c:2026-10-03:2026-01-01"))
        assertNull(SummaryPeriod.decode("m:13"))
    }

    @Test
    fun `the wire name keeps the relative names the server phrases`() {
        assertEquals("CURRENT_MONTH", SummaryPeriod.thisMonth(today).wireName(today))
        assertEquals("LAST_MONTH", SummaryPeriod.lastMonth(today).wireName(today))
        assertEquals("CURRENT_YEAR", SummaryPeriod.thisYear(today).wireName(today))
        assertEquals("LAST_YEAR", SummaryPeriod.lastYear(today).wireName(today))
        assertEquals("ALL", SummaryPeriod.All.wireName(today))
        assertEquals("March 2024", SummaryPeriod.Month(YearMonth(2024, Month.MARCH)).wireName(today))
        assertEquals("2021", SummaryPeriod.Year(2021).wireName(today))
        assertEquals("2026-07-06 to 2026-10-03", SummaryPeriod.lastDays(today, 90).wireName(today))
    }

    @Test
    fun `last 12 months starts on the first of the month eleven back`() {
        assertEquals(SummaryPeriod.Custom(LocalDate(2025, 11, 1), today), SummaryPeriod.last12Months(today))
    }

    @Test
    fun `granularity follows the window length`() {
        fun g(period: SummaryPeriod) = period.window!!.let { (s, e) -> granularityFor(s, e) }
        assertEquals(Granularity.DAY, g(SummaryPeriod.Month(YearMonth(2026, Month.JANUARY))))
        assertEquals(Granularity.DAY, g(SummaryPeriod.lastDays(today, 30)))
        assertEquals(Granularity.WEEK, g(SummaryPeriod.lastDays(today, 90)))
        assertEquals(Granularity.MONTH, g(SummaryPeriod.Year(2025)))
        assertEquals(Granularity.MONTH, g(SummaryPeriod.last12Months(today)))
        assertEquals(Granularity.YEAR, granularityFor(LocalDate(2019, 3, 1), LocalDate(2026, 10, 4)))
    }

    @Test
    fun `buckets stop at today`() {
        val (start, end) = SummaryPeriod.thisMonth(today).window!!
        assertEquals(
            listOf(LocalDate(2026, 10, 1), LocalDate(2026, 10, 2), LocalDate(2026, 10, 3)),
            bucketStarts(start, end, today, Granularity.DAY),
        )
    }

    @Test
    fun `weeks run from the window start`() {
        val start = LocalDate(2026, 7, 6)
        val buckets = bucketStarts(start, LocalDate(2026, 7, 25), today, Granularity.WEEK)
        assertEquals(listOf(LocalDate(2026, 7, 6), LocalDate(2026, 7, 13), LocalDate(2026, 7, 20)), buckets)
        assertEquals(LocalDate(2026, 7, 13), bucketStart(LocalDate(2026, 7, 19), start, Granularity.WEEK))
    }

    @Test
    fun `monthly and yearly buckets align to calendar boundaries`() {
        val months = bucketStarts(LocalDate(2026, 7, 6), LocalDate(2026, 10, 4), today, Granularity.MONTH)
        assertEquals(
            listOf(LocalDate(2026, 7, 1), LocalDate(2026, 8, 1), LocalDate(2026, 9, 1), LocalDate(2026, 10, 1)),
            months,
        )
        val years = bucketStarts(LocalDate(2024, 5, 2), LocalDate(2026, 10, 4), today, Granularity.YEAR)
        assertEquals(listOf(LocalDate(2024, 1, 1), LocalDate(2025, 1, 1), LocalDate(2026, 1, 1)), years)
    }

    @Test
    fun `a window entirely in the future has no buckets`() {
        assertEquals(emptyList(), bucketStarts(LocalDate(2026, 11, 1), LocalDate(2026, 12, 1), today, Granularity.DAY))
    }
}
