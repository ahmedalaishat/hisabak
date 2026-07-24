package com.hisabak.feature.sms.presentation.inbox

import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.ConfirmAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.DismissAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.SuggestAiParseUseCase
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
import com.hisabak.testutil.FakeAiSmsParser
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitAlertStore
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.FakeNotificationStrings
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.Test
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class SmsInboxViewModelTest : MainDispatcherTest() {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val notifier = RecordingNotifier()
    private val detector = RegexSmsTemplateDetector(listOf("Purchase of AED {amount} at {brand} done"))
    private val parser = TemplateSmsParser(Currency.AED, TimeZone.UTC)

    private val aiParser = FakeAiSmsParser()

    private val processor = SmsTransactionProcessor(
        detector = detector,
        parser = parser,
        findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
        transactionRepository = transactionRepo,
        smsRepository = smsRepo,
        clock = clock,
    )
    private val suggestAiParse =
        SuggestAiParseUseCase(aiParser, smsRepo, brandRepo, Currency.AED, clock, FakeAnalytics())
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
        strings = FakeNotificationStrings(),
    )

    private val ingest = IngestSmsUseCase(
        smsRepository = smsRepo,
        processor = processor,
        clock = clock,
        suggestAiParse = suggestAiParse,
        appScope = CoroutineScope(Dispatchers.Unconfined),
    )
    private val capture = CaptureTransactionUseCase(
        ingest = ingest,
        recordedNotifier = TransactionRecordedNotifier(
            brands = brandRepo,
            categories = FakeCategoryRepository(),
            notifier = notifier,
            currency = Currency.AED,
            strings = FakeNotificationStrings(),
        ),
        limitMonitor = limitMonitor,
        analytics = FakeAnalytics(),
    )

    private fun viewModel() = SmsInboxViewModel(
        observeMessages = ObserveSmsMessagesUseCase(smsRepo),
        capture = capture,
        deleteSms = DeleteSmsUseCase(smsRepo),
        detector = detector,
        parser = parser,
        aiParser = aiParser,
        suggestAiParse = suggestAiParse,
        confirmAiSuggestion = ConfirmAiSuggestionUseCase(smsRepo, processor, limitMonitor, FakeAnalytics()),
        dismissAiSuggestion = DismissAiSuggestionUseCase(smsRepo, FakeAnalytics()),
    )

    @Test
    fun `a parseable draft yields a live preview of brand and amount`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.DraftChanged("Purchase of AED 89.00 at Talabat done"))

        val preview = vm.state.value.draftPreview
        assertNotNull(preview)
        assertEquals("Talabat", preview!!.brandName)
        assertEquals(8_900L, preview.amount!!.amountMinor)
    }

    @Test
    fun `an unrecognized draft has no preview`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.DraftChanged("just some random text"))

        assertNull(vm.state.value.draftPreview)
    }

    @Test
    fun `ai availability is reflected in state`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(true, vm.state.value.aiAvailable)
    }

    @Test
    fun `an unavailable ai parser keeps the affordances hidden`() = runTest {
        aiParser.availability = AiParserAvailability.Unavailable
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(false, vm.state.value.aiAvailable)
    }

    @Test
    fun `rows carry the stored suggestion`() = runTest {
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "Your card was charged 12.50 at Noon",
            receivedAt = Instant.parse("2026-03-04T09:30:00Z"),
            suggested = ParsedSmsData("Noon", aed(12_50), null),
        )
        smsRepo.upsert(message)
        val vm = viewModel()
        advanceUntilIdle()

        val row = vm.state.value.rows.single()
        assertEquals("Noon", row.suggestedBrand)
        assertEquals(aed(12_50), row.suggestedAmount)
        assertNull(row.parsedBrand)
    }

    @Test
    fun `a failing manual ai parse clears its spinner and reports the failure`() = runTest {
        aiParser.result = null
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "mystery text",
            receivedAt = Instant.parse("2026-03-04T09:30:00Z"),
        )
        smsRepo.upsert(message)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.SuggestParse(message.id))
        advanceUntilIdle()

        assertTrue(vm.state.value.suggestingIds.isEmpty())
        assertEquals(SmsInboxEffect.AiParseFailed, vm.effect.value)
    }

    @Test
    fun `confirming a suggestion creates the transaction and reports it`() = runTest {
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "Your card was charged 12.50 at Noon",
            receivedAt = Instant.parse("2026-03-04T09:30:00Z"),
            suggested = ParsedSmsData("Noon", aed(12_50), Instant.parse("2026-03-04T09:30:00Z")),
        )
        smsRepo.upsert(message)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.ConfirmSuggestion(message.id))
        advanceUntilIdle()

        assertEquals(1, transactionRepo.current.size)
        assertEquals(
            SmsInboxEffect.TransactionCreated(aed(12_50)),
            vm.effect.value,
        )
        val row = vm.state.value.rows.single()
        assertEquals(true, row.isLinked)
        assertNull(row.suggestedBrand)
    }
}
