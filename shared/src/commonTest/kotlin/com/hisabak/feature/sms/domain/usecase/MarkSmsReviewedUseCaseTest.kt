package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.smsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class MarkSmsReviewedUseCaseTest {

    private val analytics = FakeAnalytics()

    private fun useCase(smsRepo: FakeSmsRepository) =
        MarkSmsReviewedUseCase(smsRepository = smsRepo, analytics = analytics)

    private val autoConfirmed = smsMessage(id = "s1", body = "AED 45.50 at CARREFOUR").copy(
        transactionId = TransactionId("t1"),
        autoConfirmed = true,
    )

    @Test
    fun `marks an auto-confirmed message reviewed`() = runTest {
        val repo = FakeSmsRepository(listOf(autoConfirmed))

        useCase(repo)(SmsMessageId("s1"))

        assertTrue(repo.current.single().reviewed)
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.AutoConfirmReviewed), analytics.logged)
    }

    /** autoConfirmed is history: reviewing says someone checked it, not that the gate didn't act. */
    @Test
    fun `keeps the auto-confirmed flag`() = runTest {
        val repo = FakeSmsRepository(listOf(autoConfirmed))

        useCase(repo)(SmsMessageId("s1"))

        assertTrue(repo.current.single().autoConfirmed)
    }

    @Test
    fun `is idempotent`() = runTest {
        val repo = FakeSmsRepository(listOf(autoConfirmed))

        useCase(repo)(SmsMessageId("s1"))
        useCase(repo)(SmsMessageId("s1"))

        assertEquals(1, analytics.logged.size)
    }

    /** A row the user confirmed never shows the tag, so there is nothing to discharge. */
    @Test
    fun `does nothing for a message the user confirmed`() = runTest {
        val userConfirmed = autoConfirmed.copy(autoConfirmed = false)
        val repo = FakeSmsRepository(listOf(userConfirmed))

        useCase(repo)(SmsMessageId("s1"))

        assertFalse(repo.current.single().reviewed)
        assertTrue(analytics.logged.isEmpty())
    }

    @Test
    fun `fails for an unknown message`() = runTest {
        val repo = FakeSmsRepository()

        val result = useCase(repo)(SmsMessageId("nope"))

        assertTrue(result is com.hisabak.core.common.DomainResult.Failure)
    }
}
