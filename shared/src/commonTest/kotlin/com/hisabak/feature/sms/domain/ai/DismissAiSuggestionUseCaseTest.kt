package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.aed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DismissAiSuggestionUseCaseTest {

    private val smsRepo = FakeSmsRepository()
    private val analytics = FakeAnalytics()
    private val dismiss = DismissAiSuggestionUseCase(smsRepo, analytics)

    @Test
    fun `dismissing clears the stored suggestion`() = runTest {
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "Your card was charged 12.50 at Noon",
            receivedAt = Instant.parse("2026-03-04T09:30:00Z"),
            suggested = ParsedSmsData("Noon", aed(12_50), null),
        )
        smsRepo.upsert(message)

        val result = dismiss(message.id)

        assertTrue(result is DomainResult.Success)
        assertNull(smsRepo.current.single().suggested)
        assertEquals(listOf("ai_suggestion_dismissed"), analytics.names())
    }

    @Test
    fun `dismissing a message without a suggestion is a quiet no op`() = runTest {
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "hello",
            receivedAt = Instant.parse("2026-03-04T09:30:00Z"),
        )
        smsRepo.upsert(message)

        val result = dismiss(message.id)

        assertTrue(result is DomainResult.Success)
        assertTrue(analytics.names().isEmpty())
    }
}
