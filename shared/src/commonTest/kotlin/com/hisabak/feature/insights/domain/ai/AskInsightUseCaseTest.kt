package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.core.domain.AskTally
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeRemoteInsightsClient
import com.hisabak.testutil.TestClock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class AskInsightUseCaseTest {

    private val client = FakeRemoteInsightsClient()
    private val prefs = FakeAppPreferences()
    private val clock = TestClock()
    private val analytics = FakeAnalytics()
    private val useCase = AskInsightUseCase(client, prefs, Currency.AED, clock, analytics)

    private suspend fun ask(question: String = "Why is dining up?", history: List<AskTurn> = emptyList()) =
        useCase(summary(), "en", question, history, AskSource.Chip)

    @Test
    fun `a question sends the summary and the last turns under one install id`() = runTest {
        val history = List(8) { AskTurn(if (it % 2 == 0) AskRole.User else AskRole.Assistant, "t$it") }

        val result = ask(history = history)

        val answered = assertIs<AskResult.Answered>(result)
        assertEquals("Dining drove it.", answered.text)
        assertEquals(ASK_DAILY_ALLOWANCE - 1, answered.remaining)
        val (request, installId) = client.asks.single()
        assertEquals("Why is dining up?", request.question)
        assertEquals(ASK_HISTORY_TURNS, request.history.size)
        assertEquals("t2", request.history.first().text)
        assertEquals(listOf("user", "assistant"), request.history.take(2).map { it.role })
        assertEquals("CURRENT_MONTH", request.summary.period)
        assertEquals(installId, prefs.installId.first())
    }

    @Test
    fun `the install id is minted once and reused`() = runTest {
        ask(); ask()

        val ids = client.asks.map { it.second }.toSet()
        assertEquals(1, ids.size)
        assertNotNull(ids.single())
        assertEquals(36, ids.single().length)
    }

    @Test
    fun `the allowance counts down and refuses locally when spent`() = runTest {
        repeat(ASK_DAILY_ALLOWANCE) { assertIs<AskResult.Answered>(ask()) }

        assertEquals(0, useCase.remaining())
        assertEquals(AskResult.NoQuestionsLeft, ask())
        assertEquals(ASK_DAILY_ALLOWANCE, client.asks.size)
        assertTrue(analytics.logged.any { it is AnalyticsEvent.InsightsQuotaHit })
    }

    @Test
    fun `the allowance resets with the local date`() = runTest {
        prefs.setAskTally(AskTally(day = clock.today().toString(), count = ASK_DAILY_ALLOWANCE))
        assertEquals(0, useCase.remaining())

        clock.now += 1.days

        assertEquals(ASK_DAILY_ALLOWANCE, useCase.remaining())
        assertIs<AskResult.Answered>(ask())
        assertEquals(1, prefs.askTally.first().count)
    }

    @Test
    fun `an outage is unavailable and does not spend the allowance`() = runTest {
        client.answer = null

        assertEquals(AskResult.Unavailable, ask())
        assertEquals(ASK_DAILY_ALLOWANCE, useCase.remaining())
    }

    @Test
    fun `a blank question is never sent`() = runTest {
        assertEquals(AskResult.Unavailable, ask("   "))
        assertTrue(client.asks.isEmpty())
    }

    @Test
    fun `the question is cut to the wire limit`() = runTest {
        ask("x".repeat(MAX_QUESTION_LENGTH + 50))

        assertEquals(MAX_QUESTION_LENGTH, client.asks.single().first.question.length)
    }

    @Test
    fun `analytics carry the source and the on-topic flag but never the words`() = runTest {
        client.answer = AskResponseDto(answer = "I can only discuss this review.", onTopic = false)

        useCase(summary(), "en", "write me a poem", emptyList(), AskSource.Free)

        val event = analytics.logged.filterIsInstance<AnalyticsEvent.InsightsAsk>().single()
        assertEquals(mapOf("source" to "free", "on_topic" to false), event.params)
    }
}
