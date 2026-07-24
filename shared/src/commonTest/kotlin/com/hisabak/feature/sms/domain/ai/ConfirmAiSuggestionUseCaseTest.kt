package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitAlertStore
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.FakeNotificationStrings
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class ConfirmAiSuggestionUseCaseTest {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val analytics = FakeAnalytics()

    private val processor = SmsTransactionProcessor(
        detector = RegexSmsTemplateDetector(listOf("Purchase of AED {amount} at {brand} done")),
        parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
        findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
        transactionRepository = transactionRepo,
        smsRepository = smsRepo,
        clock = clock,
    )

    private val limitMonitor = CategoryLimitMonitor(
        transactions = transactionRepo,
        brands = brandRepo,
        categories = FakeCategoryRepository(),
        limits = FakeCategoryLimitRepository(),
        notifications = FakeNotificationRepository(),
        alertStore = FakeCategoryLimitAlertStore(),
        systemNotifier = RecordingNotifier(),
        currency = Currency.AED,
        clock = clock,
        strings = FakeNotificationStrings(),
    )

    private val confirm = ConfirmAiSuggestionUseCase(smsRepo, processor, limitMonitor, analytics)

    private val occurredAt = Instant.parse("2026-03-04T09:30:00Z")
    private val suggestion = ParsedSmsData("Noon", aed(12_50), occurredAt)

    private suspend fun storedMessage(suggested: ParsedSmsData? = suggestion): SmsMessage {
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "Your card was charged 12.50 at Noon",
            receivedAt = occurredAt,
            suggested = suggested,
        )
        smsRepo.upsert(message)
        return message
    }

    @Test
    fun `confirming creates the brand and transaction and links the message`() = runTest {
        val message = storedMessage()

        val result = confirm(message.id)

        assertTrue(result is DomainResult.Success)
        val tx = (result as DomainResult.Success).value
        assertEquals(aed(12_50), tx.amount)
        assertEquals(occurredAt, tx.occurredAt)
        assertEquals("Noon", brandRepo.current.single().name)

        val stored = smsRepo.current.single()
        assertEquals(tx.id, stored.transactionId)
        assertEquals(suggestion, stored.parsed)
        assertNull(stored.suggested)
        assertEquals(listOf("ai_suggestion_confirmed"), analytics.names())
    }

    @Test
    fun `a message without a suggestion cannot be confirmed`() = runTest {
        val message = storedMessage(suggested = null)

        val result = confirm(message.id)

        assertTrue(result is DomainResult.Failure)
        assertTrue(transactionRepo.current.isEmpty())
    }

    @Test
    fun `an already linked message cannot be confirmed again`() = runTest {
        val message = storedMessage()
        confirm(message.id)
        val firstCount = transactionRepo.current.size

        val second = confirm(message.id)

        assertTrue(second is DomainResult.Failure)
        assertEquals(firstCount, transactionRepo.current.size)
    }
}
