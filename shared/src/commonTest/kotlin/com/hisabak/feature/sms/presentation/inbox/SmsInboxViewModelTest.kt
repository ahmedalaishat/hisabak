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
import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.feature.sms.domain.ai.ConfirmAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.DismissAiSuggestionUseCase
import com.hisabak.feature.sms.domain.ai.SuggestAiParseUseCase
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.template.DeleteSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.PreviewSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SaveSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SynthesizeTemplateUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ImportParsedSmsUseCase
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
import com.hisabak.testutil.FakeAiSmsParser
import com.hisabak.core.common.AppConfig
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitAlertStore
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.FakeNotificationStrings
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import com.hisabak.testutil.smsMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class SmsInboxViewModelTest : MainDispatcherTest() {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val notifier = RecordingNotifier()
    private val templateRepo = FakeSmsTemplateRepository()
    private val synthesizeTemplate = SynthesizeTemplateUseCase(
        repository = templateRepo,
        saveTemplate = SaveSmsTemplateUseCase(templateRepo, clock, FakeAnalytics()),
        previewTemplate = PreviewSmsTemplateUseCase(smsRepo),
        clock = clock,
        analytics = FakeAnalytics(),
    )
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
        autoConfirmSuggestion = { _, _ -> null },
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
        importParsed = ImportParsedSmsUseCase(
            smsRepository = smsRepo,
            processor = processor,
            limitMonitor = limitMonitor,
            analytics = FakeAnalytics(),
        ),
        deleteSms = DeleteSmsUseCase(smsRepo),
        detector = detector,
        parser = parser,
        aiParser = aiParser,
        suggestAiParse = suggestAiParse,
        confirmAiSuggestion = ConfirmAiSuggestionUseCase(
            smsRepo, processor, limitMonitor, synthesizeTemplate, FakeAnalytics(),
        ),
        deleteTemplate = DeleteSmsTemplateUseCase(templateRepo, FakeAnalytics()),
        preferences = FakeAppPreferences(),
        // No parse service in this build, so the online-parsing offer never appears and these
        // tests stay about the inbox rather than the prompts.
        appConfig = AppConfig(
            seedData = false, smsAutoCapture = false, isDebug = true, versionCode = 1, flavor = "test",
        ),
        analytics = FakeAnalytics(),
        dismissAiSuggestion = DismissAiSuggestionUseCase(smsRepo, FakeAnalytics()),
    )

    @Test
    fun `an unmatched paste with ai ready stores the entry and drives a confirm-first suggestion`() = runTest {
        aiParser.freeTextResult = AiParsedSms("Noon", 12_50, "AED", null)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.DraftChanged("lunch 12.50 at noon yesterday"))
        vm.onIntent(SmsInboxIntent.IngestDraft)
        advanceUntilIdle()

        // No dead-end error: the draft cleared, the row exists, the suggestion arrived through
        // the free-text prompt, and nothing was auto-created.
        assertNull(vm.effect.value)
        assertEquals("", vm.state.value.draftBody)
        assertEquals("Noon", smsRepo.current.single().suggested?.brandName)
        assertEquals(1, aiParser.parsedFreeTexts.size)
        assertTrue(aiParser.parsedBodies.isEmpty())
        assertTrue(transactionRepo.current.isEmpty())
        assertTrue(vm.state.value.suggestingIds.isEmpty()) // spinner cleared after completion
    }

    @Test
    fun `an eager paste before the availability flag settles still gets its suggestion`() = runTest {
        // The paste path asks the parser directly rather than trusting state.aiAvailable,
        // which an init coroutine populates — losing that race must not cost the suggestion.
        aiParser.freeTextResult = AiParsedSms("Noon", 12_50, "AED", null)
        val vm = viewModel()
        // Deliberately no advanceUntilIdle: state.aiAvailable may still be false here.

        vm.onIntent(SmsInboxIntent.DraftChanged("coffee 12.50"))
        vm.onIntent(SmsInboxIntent.IngestDraft)
        advanceUntilIdle()

        assertEquals("Noon", smsRepo.current.single().suggested?.brandName)
        assertNull(vm.effect.value)
    }

    @Test
    fun `an unmatched paste with a failing model surfaces the ai snackbar`() = runTest {
        aiParser.freeTextResult = null
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.DraftChanged("gibberish"))
        vm.onIntent(SmsInboxIntent.IngestDraft)
        advanceUntilIdle()

        assertEquals(SmsInboxEffect.AiParseFailed, vm.effect.value)
        assertEquals(1, smsRepo.current.size) // still stored for template-teaching
        assertTrue(vm.state.value.suggestingIds.isEmpty())
    }

    @Test
    fun `an unmatched paste without ai keeps the clear parse-failed error and clears the draft`() = runTest {
        aiParser.availability = AiParserAvailability.Unavailable
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.DraftChanged("lunch 45"))
        vm.onIntent(SmsInboxIntent.IngestDraft)
        advanceUntilIdle()

        assertTrue(vm.effect.value is SmsInboxEffect.ParseFailed)
        assertEquals("", vm.state.value.draftBody) // it IS stored — keeping the text invites double-pastes
        assertEquals(1, smsRepo.current.size)
        assertTrue(aiParser.parsedFreeTexts.isEmpty())
    }

    @Test
    fun `import on a parsed unlinked row recreates its transaction`() = runTest {
        // The row a captured message becomes after its transaction is deleted: parsed, with
        // Import — which must re-import THAT message, not the empty paste draft.
        smsRepo.upsert(
            smsMessage(id = "s1", body = "Purchase of AED 89.00 at Talabat done")
                .copy(parsed = ParsedSmsData("Talabat", aed(89_00), clock.now())),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.ImportParsed(SmsMessageId("s1")))
        advanceUntilIdle()

        assertEquals(SmsInboxEffect.TransactionCreated(aed(89_00)), vm.effect.value)
        assertTrue(vm.state.value.rows.single().isLinked)
        assertEquals(89_00L, transactionRepo.current.single().amount.amountMinor)
    }

    @Test
    fun `a linked ai-parsed row exposes its transaction id for review`() = runTest {
        smsRepo.upsert(
            smsMessage(id = "s1", body = "mystery charge")
                .copy(suggested = ParsedSmsData("Noon", aed(12_50), clock.now())),
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.ConfirmSuggestion(SmsMessageId("s1")))
        advanceUntilIdle()

        val row = vm.state.value.rows.single()
        assertTrue(row.isLinked)
        assertEquals(transactionRepo.current.single().id, row.transactionId)
    }

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
        // Kept as the "AI parsed" provenance marker on the linked row.
        assertEquals("Noon", row.suggestedBrand)
    }

    @Test
    fun `confirming a suggestion with a derived pattern reports the learned template`() = runTest {
        val at = Instant.parse("2026-03-04T09:30:00Z")
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "Your card was charged 12.50 at Noon",
            receivedAt = at,
            suggested = ParsedSmsData("Noon", aed(12_50), at),
            suggestedPattern = "Your card was charged {amount} at {brand}",
        )
        smsRepo.upsert(message)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsInboxIntent.ConfirmSuggestion(message.id))
        advanceUntilIdle()

        val effect = vm.effect.value as SmsInboxEffect.TransactionCreated
        val learned = assertNotNull(effect.learnedTemplateId)
        assertEquals(learned, templateRepo.current.single().id)

        vm.onIntent(SmsInboxIntent.UndoLearnedTemplate(learned))
        advanceUntilIdle()

        assertTrue(templateRepo.current.isEmpty())
    }
}
