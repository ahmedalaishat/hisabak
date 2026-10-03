package com.hisabak.feature.dashboard.domain

import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.categoryLimit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

class PeriodLimitTest {

    private val dining = CategoryId("dining")
    private val limits = listOf(categoryLimit("dining", 3_000_00, effectiveFrom = YearMonth(2026, 1)))

    @Test
    fun `a whole month gets its cap`() {
        assertEquals(3_000_00, limitBetween(limits, dining, LocalDate(2026, 9, 1), LocalDate(2026, 10, 1)))
    }

    @Test
    fun `a whole year gets twelve caps`() {
        assertEquals(36_000_00, limitBetween(limits, dining, LocalDate(2026, 1, 1), LocalDate(2027, 1, 1)))
    }

    @Test
    fun `a partial month gets its share of the days`() {
        // 15 of September's 30 days.
        assertEquals(1_500_00, limitBetween(limits, dining, LocalDate(2026, 9, 16), LocalDate(2026, 10, 1)))
    }

    @Test
    fun `a span across months prorates each one`() {
        // 16 of August's 31 days + all of September + 3 of October's 31.
        val expected = 3_000_00 * 16.0 / 31 + 3_000_00 + 3_000_00 * 3.0 / 31
        assertEquals(kotlin.math.round(expected).toLong(), limitBetween(limits, dining, LocalDate(2026, 8, 16), LocalDate(2026, 10, 4)))
    }

    @Test
    fun `months before the cap took effect add nothing`() {
        val late = listOf(categoryLimit("dining", 3_000_00, effectiveFrom = YearMonth(2026, 9)))
        assertEquals(3_000_00, limitBetween(late, dining, LocalDate(2026, 8, 1), LocalDate(2026, 10, 1)))
    }

    @Test
    fun `no cap at all is null rather than zero`() {
        assertNull(limitBetween(emptyList(), dining, LocalDate(2026, 9, 1), LocalDate(2026, 10, 1)))
        assertNull(limitBetween(limits, CategoryId("other"), LocalDate(2026, 9, 1), LocalDate(2026, 10, 1)))
    }
}
