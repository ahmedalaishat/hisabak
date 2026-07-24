package com.hisabak.feature.sms.domain.capture

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitAlertStore
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlinx.datetime.TimeZone

class CaptureTransactionUseCaseTest {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val notifier = RecordingNotifier()

    private val ingest = IngestSmsUseCase(
        smsRepository = smsRepo,
        processor = SmsTransactionProcessor(
            detector = RegexSmsTemplateDetector(listOf("Purchase of AED {amount} at {brand} done")),
            parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
            findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
            transactionRepository = transactionRepo,
            smsRepository = smsRepo,
            clock = clock,
        ),
        clock = clock,
    )

    private val recordedNotifier = TransactionRecordedNotifier(
        brands = brandRepo,
        categories = FakeCategoryRepository(),
        notifier = notifier,
        currency = Currency.AED,
        strings = com.hisabak.testutil.FakeNotificationStrings(),
    )

    private val limitMonitor = CategoryLimitMonitor(
        transactions = transactionRepo,
        brands = brandRepo,
        categories = FakeCategoryRepository(),
        limits = FakeCategoryLimitRepository(),
        notifications = FakeNotificationRepository(),
        alertStore = FakeCategoryLimitAlertStore(),
        systemNotifier = notifier,
        currency = Currency.AED,
        clock = clock,
        strings = com.hisabak.testutil.FakeNotificationStrings(),
    )

    private val analytics = FakeAnalytics()

    private val capture = CaptureTransactionUseCase(ingest, recordedNotifier, limitMonitor, analytics)

    @Test
    fun `external source posts the recorded confirmation`() = runTest {
        val result = capture("Purchase of AED 42.00 at Lulu done", CaptureSource.SHARE)

        assertTrue(result is DomainResult.Success)
        assertEquals(1, transactionRepo.current.size)
        assertEquals(1, notifier.recorded.size)

        val event = analytics.logged.single() as AnalyticsEvent.SmsCaptured
        assertEquals("sms_captured", event.name)
        assertEquals("share", event.params["source"])
        // PII guard: no raw amount or brand name in the event.
        assertTrue(event.params.values.none { it == "Lulu" || it == 4_200L || it == "42.00" })
    }

    @Test
    fun `shortcut capture saves and posts the recorded confirmation with its source`() = runTest {
        val result = capture("Purchase of AED 42.00 at Lulu done", CaptureSource.SHORTCUT)

        assertTrue(result is DomainResult.Success)
        assertEquals(1, transactionRepo.current.size)
        assertEquals(1, notifier.recorded.size)

        val event = analytics.logged.single() as AnalyticsEvent.SmsCaptured
        assertEquals("shortcut", event.params["source"])
    }

    @Test
    fun `manual paste saves the transaction but posts no confirmation`() = runTest {
        val result = capture("Purchase of AED 42.00 at Lulu done", CaptureSource.MANUAL_PASTE)

        assertTrue(result is DomainResult.Success)
        assertEquals(1, transactionRepo.current.size)
        assertTrue(notifier.recorded.isEmpty())
    }

    @Test
    fun `unparseable text records nothing and notifies nothing`() = runTest {
        val result = capture("just a normal text message", CaptureSource.SHARE)

        assertTrue(result is DomainResult.Failure)
        assertTrue(transactionRepo.current.isEmpty())
        assertTrue(notifier.recorded.isEmpty())
        assertEquals(listOf("sms_parse_failed"), analytics.names())
    }
}
