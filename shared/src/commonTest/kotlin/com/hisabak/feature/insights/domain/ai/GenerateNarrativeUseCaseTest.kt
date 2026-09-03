package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeNarrativeCache
import com.hisabak.testutil.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GenerateNarrativeUseCaseTest {

    private class ScriptedAiInsights(
        var enabled: Boolean = true,
        var reply: List<RawNarrativeInsight>? = listOf(raw()),
    ) : AiInsights {
        var calls = 0
        override suspend fun isEnabled() = enabled
        override suspend fun narrate(summary: InsightsSummary, language: String): List<RawNarrativeInsight>? {
            calls++
            return reply
        }
    }

    private val ai = ScriptedAiInsights()
    private val cache = FakeNarrativeCache()
    private val analytics = FakeAnalytics()
    private val useCase = GenerateNarrativeUseCase(ai, cache, TestClock(), analytics)

    @Test
    fun `disabled means no call and no cache write`() = runTest {
        ai.enabled = false

        assertEquals(NarrativeResult.Disabled, useCase(summary(), "en"))
        assertEquals(0, ai.calls)
        assertTrue(cache.entries.isEmpty())
    }

    @Test
    fun `a fresh narrative is sanitized then cached and counted`() = runTest {
        ai.reply = listOf(raw(categoryId = "dining"), raw(categoryId = "ghost"))

        val result = useCase(summary(), "en")

        val ready = assertIs<NarrativeResult.Ready>(result)
        assertTrue(ready.fresh)
        assertEquals(listOf("dining"), ready.items.map { it.category?.id?.value })
        assertEquals(1, cache.entries[SummaryPeriod.CURRENT_MONTH]!!.items.size)
        val event = analytics.logged.filterIsInstance<AnalyticsEvent.InsightsNarrativeGenerated>().single()
        assertEquals(1, event.params["count"])
        assertEquals(1, event.params["dropped"])
    }

    @Test
    fun `the same key is served from the cache without a call`() = runTest {
        useCase(summary(), "en")

        val again = useCase(summary(uncategorized = 341_00), "en")

        assertEquals(1, ai.calls)
        assertEquals(false, assertIs<NarrativeResult.Ready>(again).fresh)
    }

    @Test
    fun `a material change costs exactly one more call`() = runTest {
        useCase(summary(), "en")

        useCase(summary(income = 20_000_00), "en")

        assertEquals(2, ai.calls)
    }

    @Test
    fun `an outage returns the stale narrative when there is one and nothing when there is not`() = runTest {
        ai.reply = null
        assertNull(assertIs<NarrativeResult.Unavailable>(useCase(summary(), "en")).stale)

        ai.reply = listOf(raw())
        useCase(summary(), "en")
        ai.reply = null

        val stale = assertIs<NarrativeResult.Unavailable>(useCase(summary(income = 20_000_00), "en")).stale
        assertEquals(1, stale!!.size)
    }

    @Test
    fun `an empty reply is cached so a quiet period is not re-sent on every open`() = runTest {
        ai.reply = emptyList()

        useCase(summary(), "en")
        useCase(summary(), "en")

        assertEquals(1, ai.calls)
    }

    @Test
    fun `an empty ledger is never sent`() = runTest {
        val empty = summary(categories = emptyList(), income = 0, uncategorized = 0, uncategorizedCount = 0)

        val result = useCase(empty, "en")

        assertEquals(0, ai.calls)
        assertTrue(assertIs<NarrativeResult.Ready>(result).items.isEmpty())
    }
}
