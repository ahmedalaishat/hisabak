package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.SummaryPeriod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class NarrativeKeyTest {

    @Test
    fun `the same input gives the same key`() {
        assertEquals(narrativeKey(summary(), "en"), narrativeKey(summary(), "en"))
    }

    @Test
    fun `any change to any figure changes the key`() {
        val base = narrativeKey(summary(), "en")

        assertNotEquals(base, narrativeKey(summary(uncategorized = 340_01), "en"))       // one fils
        assertNotEquals(base, narrativeKey(summary(uncategorizedCount = 4), "en"))
        assertNotEquals(base, narrativeKey(summary(income = 12_500_01), "en"))
        assertNotEquals(base, narrativeKey(summary(priorExpense = 6_900_01), "en"))
        assertNotEquals(
            base,
            narrativeKey(summary(categories = listOf(spend("dining", spent = 1_800_00, prior = 1_200_00, limit = 1_500_00))), "en"),
        )
        assertNotEquals(
            base,
            narrativeKey(
                summary(
                    categories = listOf(
                        spend("dining", spent = 1_800_00, prior = 1_200_00, limit = 1_500_00, name = "Eating out"),
                        spend("fuel", spent = 400_00, prior = 420_00),
                    ),
                ),
                "en",
            ),
        )
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
    fun `the language is part of the key`() {
        assertNotEquals(narrativeKey(summary(), "en"), narrativeKey(summary(), "ar"))
    }

    @Test
    fun `the key is a fixed-width digest`() {
        assertEquals(16, narrativeKey(summary(), "en").length)
        assertEquals("cbf29ce484222325", fnv1a64(""))
        assertEquals("af63dc4c8601ec8c", fnv1a64("a"))
    }
}
