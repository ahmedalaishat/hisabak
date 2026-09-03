package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.SummaryPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NarrativeKeyTest {

    @Test
    fun `rounds to two significant digits`() {
        assertEquals(0, roundToTwoSignificant(0))
        assertEquals(99, roundToTwoSignificant(99))
        assertEquals(120, roundToTwoSignificant(123))
        assertEquals(1_200_00, roundToTwoSignificant(1_234_56))
        assertEquals(9_900_000, roundToTwoSignificant(9_987_654))
    }

    @Test
    fun `a small transaction does not change the key`() {
        val before = summary(uncategorized = 340_00)
        val after = summary(uncategorized = 342_50)

        assertEquals(narrativeKey(before, "en"), narrativeKey(after, "en"))
    }

    @Test
    fun `a finding appearing changes the key`() {
        val under = summary(categories = listOf(spend("dining", spent = 1_000_00, prior = 1_000_00, limit = 1_500_00)))
        val over = summary(categories = listOf(spend("dining", spent = 1_600_00, prior = 1_000_00, limit = 1_500_00)))

        assertNotEquals(narrativeKey(under, "en"), narrativeKey(over, "en"))
    }

    @Test
    fun `a total moving in its second significant digit changes the key`() {
        val a = summary(income = 12_000_00)
        val b = summary(income = 13_000_00)

        assertNotEquals(narrativeKey(a, "en"), narrativeKey(b, "en"))
    }

    @Test
    fun `the period's window is part of the key even when the figures coincide`() {
        // Every transaction in the current month: this month and this year read the same.
        val month = summary(period = SummaryPeriod.CURRENT_MONTH)
        val year = summary(period = SummaryPeriod.CURRENT_YEAR)
        val all = summary(period = SummaryPeriod.ALL)

        assertNotEquals(narrativeKey(month, "en"), narrativeKey(year, "en"))
        assertNotEquals(narrativeKey(year, "en"), narrativeKey(all, "en"))
    }

    @Test
    fun `the key is a fixed-width digest`() {
        val key = narrativeKey(summary(), "en")

        assertEquals(16, key.length)
        assertEquals(key, narrativeKey(summary(), "en"))
        assertEquals("cbf29ce484222325", fnv1a64(""))
        assertEquals("af63dc4c8601ec8c", fnv1a64("a"))
    }

    @Test
    fun `the language is part of the key`() {
        assertNotEquals(narrativeKey(summary(), "en"), narrativeKey(summary(), "ar"))
    }
}
