package com.hisabak.feature.transaction.domain

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavingsRateTest {

    @Test
    fun `keeping half of income reads as one half`() {
        assertEquals(0.5, savingsRate(10_000L, 5_000L))
    }

    @Test
    fun `spending nothing keeps all of it`() {
        assertEquals(1.0, savingsRate(10_000L, 0L))
    }

    @Test
    fun `break-even is zero rather than the middle of the scale`() {
        // The point of the change: the old metric put break-even at 50%, which reads as healthy.
        assertEquals(0.0, savingsRate(10_000L, 10_000L))
    }

    @Test
    fun `overspending goes negative`() {
        assertEquals(-0.5, savingsRate(10_000L, 15_000L))
        assertEquals(-1.0, savingsRate(10_000L, 20_000L))
    }

    @Test
    fun `no income means no rate`() {
        assertNull(savingsRate(0L, 5_000L), "a rate before payday would be a lie")
        assertNull(savingsRate(0L, 0L))
        assertNull(savingsRate(-100L, 5_000L))
    }

    @Test
    fun `a real month reports what you would compute by hand`() {
        // August 2026 from the production database: 15,400 income against 7,286 of expenses.
        val rate = savingsRate(1_540_000L, 728_600L)!!
        // Rounded the way the bar's label rounds it — 52.7% shows as 53%, not 52%.
        assertEquals(53, (rate * 100).roundToInt())
        assertTrue(rate > 0.5 && rate < 0.54)
    }
}
