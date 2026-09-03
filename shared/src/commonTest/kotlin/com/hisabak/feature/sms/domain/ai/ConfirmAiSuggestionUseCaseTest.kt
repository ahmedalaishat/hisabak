package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.LearnBrandAliasUseCase
import com.hisabak.feature.brand.domain.usecase.ResolveBrandUseCase
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.template.PreviewSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SaveSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SynthesizeTemplateUseCase
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.testutil.brand
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
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
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
        findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo, ResolveBrandUseCase(brandRepo)),
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

    private val templateRepo = FakeSmsTemplateRepository()
    private val synthesize = SynthesizeTemplateUseCase(
        repository = templateRepo,
        saveTemplate = SaveSmsTemplateUseCase(templateRepo, clock, analytics),
        previewTemplate = PreviewSmsTemplateUseCase(smsRepo),
        clock = clock,
        analytics = analytics,
    )

    private val confirm = ConfirmAiSuggestionUseCase(smsRepo, processor, limitMonitor, synthesize, LearnBrandAliasUseCase(brandRepo, ResolveBrandUseCase(brandRepo), FakeAnalytics()), analytics)

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
        val tx = (result as DomainResult.Success).value.transaction
        assertEquals(aed(12_50), tx.amount)
        assertEquals(occurredAt, tx.occurredAt)
        assertEquals("Noon", brandRepo.current.single().name)

        val stored = smsRepo.current.single()
        assertEquals(tx.id, stored.transactionId)
        assertEquals(suggestion, stored.parsed)
        // Retained on purpose: the suggestion doubles as the "AI parsed" provenance marker.
        assertEquals(suggestion, stored.suggested)
        assertEquals(listOf("ai_suggestion_confirmed"), analytics.names())
    }

    @Test
    fun `confirming installs the template derived at suggest time`() = runTest {
        val message = storedMessage().let {
            val withPattern = it.copy(suggestedPattern = "Your card was charged {amount} at {brand}")
            smsRepo.upsert(withPattern)
            withPattern
        }

        val result = confirm(message.id)

        val learned = assertNotNull((result as DomainResult.Success).value.learnedTemplateId)
        val template = templateRepo.current.single()
        assertEquals(learned, template.id)
        assertTrue(template.derivedByAi)
    }

    @Test
    fun `a message with no derived pattern still confirms and reports nothing learned`() = runTest {
        val message = storedMessage()

        val result = confirm(message.id)

        assertNull((result as DomainResult.Success).value.learnedTemplateId)
        assertTrue(templateRepo.current.isEmpty())
    }

    @Test
    fun `a rejected template never costs the user their transaction`() = runTest {
        val message = storedMessage().let {
            // Too little literal anchor to be trusted as a parsing rule.
            val withPattern = it.copy(suggestedPattern = "{amount} at {brand}")
            smsRepo.upsert(withPattern)
            withPattern
        }

        val result = confirm(message.id)

        assertTrue(result is DomainResult.Success)
        assertNull((result as DomainResult.Success).value.learnedTemplateId)
        assertEquals(aed(12_50), result.value.transaction.amount)
        assertTrue(templateRepo.current.isEmpty())
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

    /**
     * The reported bug, end to end. A real message —
     * "...used for AED48.99 ... at GOOGLEYOUTUBE,US. Avl.Bal..." — parsed to the brand
     * "Youtube video", and confirming learned a template that captures the merchant *as written*.
     * The next message of that format then went through the regex path, where
     * "GOOGLEYOUTUBE,US" shares no containment with "Youtube video", and a second brand appeared
     * — silently, on the background path, with no category. Confirming now also learns the alias,
     * so the template's capture resolves to the brand the user actually chose.
     */
    @Test
    fun `a learned template resolves the merchant to the brand the confirm chose`() = runTest {
        brandRepo.emit(listOf(brand(id = "b1", name = "Youtube video", categoryId = CategoryId("c1"))))
        val first = SmsMessage(
            id = SmsMessageId("m1"),
            body = "Purchase of AED 48.99 at GOOGLEYOUTUBE,US done",
            receivedAt = clock.now(),
            suggested = ParsedSmsData("Youtube video", aed(48_99), clock.now()),
            suggestedBrandRaw = "GOOGLEYOUTUBE,US",
        )
        smsRepo.upsert(first)

        val confirmed = confirm(first.id)
        assertTrue(confirmed is DomainResult.Success)
        assertEquals("b1", (confirmed as DomainResult.Success).value.transaction.brandId.value)

        // The same bank format again — this time down the regex path, as it would be in the app
        // once the template is installed.
        val second = SmsMessage(
            id = SmsMessageId("m2"),
            body = "Purchase of AED 12.00 at GOOGLEYOUTUBE,US done",
            receivedAt = clock.now(),
        )
        smsRepo.upsert(second)
        val reparsed = processor.process(second)

        assertEquals("b1", (reparsed as DomainResult.Success).value.brandId.value)
        assertEquals(1, brandRepo.current.size)
    }

    @Test
    fun `confirming does not learn an alias the name rules already cover`() = runTest {
        brandRepo.emit(listOf(brand(id = "b1", name = "Carrefour", categoryId = CategoryId("c1"))))
        val message = SmsMessage(
            id = SmsMessageId("m1"),
            body = "Purchase of AED 48.99 at CARREFOUR done",
            receivedAt = clock.now(),
            suggested = ParsedSmsData("Carrefour", aed(48_99), clock.now()),
            suggestedBrandRaw = "CARREFOUR",
        )
        smsRepo.upsert(message)

        confirm(message.id)

        assertNull(brandRepo.findByAlias("CARREFOUR"))
    }
}
