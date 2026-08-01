package com.hisabak.feature.sms.presentation.templates

import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.sms.data.parser.ObservingSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.template.PreviewSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.SaveSmsTemplateUseCase
import com.hisabak.feature.sms.domain.template.TagRole
import com.hisabak.feature.sms.domain.template.TemplateValidationError
import com.hisabak.feature.sms.domain.usecase.ReparseSmsMessageUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherTest
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.parserTemplate
import com.hisabak.testutil.smsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class SmsTemplateEditViewModelTest : MainDispatcherTest() {

    private val clock = TestClock()
    private val analytics = FakeAnalytics()
    private val templateRepo = FakeSmsTemplateRepository()
    private val smsRepo = FakeSmsRepository()
    private val tabby =
        "You spent AED 35.00 at HARDEES-WTC MALL. Your available Tabby Card limit is now AED 8,342.27."

    private val txRepo = FakeTransactionRepository()
    private val brandRepo = FakeBrandRepository()

    private fun viewModel(templateId: SmsTemplateId? = null, smsId: SmsMessageId? = null) =
        SmsTemplateEditViewModel(
            templateId = templateId,
            sampleSmsId = smsId,
            templateRepository = templateRepo,
            smsRepository = smsRepo,
            saveTemplate = SaveSmsTemplateUseCase(templateRepo, clock, analytics),
            previewTemplate = PreviewSmsTemplateUseCase(smsRepo),
            reparseSms = ReparseSmsMessageUseCase(
                smsRepository = smsRepo,
                // An observing detector over the same template repo — the template saved a
                // moment ago is what the reparse must find, exactly as in production.
                processor = SmsTransactionProcessor(
                    detector = ObservingSmsTemplateDetector(
                        templateRepo,
                        CoroutineScope(Dispatchers.Unconfined),
                    ),
                    parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
                    findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
                    transactionRepository = txRepo,
                    smsRepository = smsRepo,
                    clock = clock,
                ),
            ),
        )

    @Test
    fun `pasting a sample auto-suggests tags and derives a pattern`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsTemplateEditIntent.SampleChanged(tabby))
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s.tokens.isNotEmpty())
        assertTrue(s.pattern.contains("{amount}"))
        // The suggested amount is the one next to the currency, not the trailing balance.
        assertEquals("35.00", s.previewAmount)
        assertTrue(s.validationError == null)
    }

    @Test
    fun `tapping a token with the active role tags and untags it`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        val sample = "Paid AED 12.50 to CAFE DELUXE ref 12345"
        vm.onIntent(SmsTemplateEditIntent.SampleChanged(sample))
        advanceUntilIdle()

        val cafeIndex = vm.state.value.tokens.indexOfFirst { it.text == "CAFE" }
        vm.onIntent(SmsTemplateEditIntent.RoleSelected(TagRole.BRAND))
        vm.onIntent(SmsTemplateEditIntent.TokenTapped(cafeIndex))
        assertEquals(TagRole.BRAND, vm.state.value.tokens[cafeIndex].role)

        vm.onIntent(SmsTemplateEditIntent.TokenTapped(cafeIndex))
        assertEquals(null, vm.state.value.tokens[cafeIndex].role)
    }

    @Test
    fun `opening from an inbox message preloads its body as the sample`() = runTest {
        smsRepo.upsert(smsMessage(id = "s9", body = tabby))
        val vm = viewModel(smsId = SmsMessageId("s9"))
        advanceUntilIdle()

        assertEquals(tabby, vm.state.value.sample)
        assertTrue(vm.state.value.pattern.contains("{amount}"))
    }

    @Test
    fun `saving stores the template and emits Saved`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onIntent(SmsTemplateEditIntent.SampleChanged(tabby))
        advanceUntilIdle()

        vm.onIntent(SmsTemplateEditIntent.Save)
        advanceUntilIdle()

        assertEquals(SmsTemplateEditEffect.Saved, vm.effect.value)
        assertEquals(tabby, templateRepo.current.single().sampleBody)
    }

    @Test
    fun `saving a template made from an inbox message imports that message through it`() = runTest {
        smsRepo.upsert(smsMessage(id = "s9", body = tabby))
        val vm = viewModel(smsId = SmsMessageId("s9"))
        advanceUntilIdle()

        vm.onIntent(SmsTemplateEditIntent.Save)
        advanceUntilIdle()

        assertEquals(SmsTemplateEditEffect.Saved, vm.effect.value)
        val message = smsRepo.current.single()
        assertTrue(message.isLinked)
        assertEquals("HARDEES-WTC MALL", message.parsed?.brandName)
        assertEquals(3_500L, txRepo.current.single().amount.amountMinor)
    }

    @Test
    fun `a degenerate sample blocks save with the anchor error`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsTemplateEditIntent.SampleChanged("100 at noon"))
        advanceUntilIdle()

        assertEquals(TemplateValidationError.InsufficientAnchor, vm.state.value.validationError)
        assertEquals(false, vm.state.value.canSave)
        vm.onIntent(SmsTemplateEditIntent.Save)
        advanceUntilIdle()
        assertTrue(templateRepo.current.isEmpty())
    }

    @Test
    fun `editing a user template reconstructs its tags from the stored sample`() = runTest {
        templateRepo.upsert(
            parserTemplate(
                id = "mine",
                pattern = "You spent AED {amount} at {brand}. Your available Tabby Card limit is now AED {ignore}.",
                sampleBody = tabby,
            ),
        )
        val vm = viewModel(templateId = SmsTemplateId("mine"))
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(tabby, s.sample)
        assertEquals("35.00", s.previewAmount)
        assertEquals("HARDEES-WTC MALL", s.previewBrand)
        val amountToken = s.tokens.first { it.text == "35.00" }
        assertEquals(TagRole.AMOUNT, amountToken.role)
    }

    @Test
    fun `opening a default template is read-only`() = runTest {
        templateRepo.upsert(
            parserTemplate(id = "default-0", pattern = "Purchase of AED {amount} with {card} at {brand},", isDefault = true),
        )
        val vm = viewModel(templateId = SmsTemplateId("default-0"))
        advanceUntilIdle()

        assertTrue(vm.state.value.isDefaultTemplate)
        assertEquals(false, vm.state.value.canSave)
        assertNotNull(vm.state.value.pattern)
    }

    @Test
    fun `the inbox preview reports matches after the debounce`() = runTest {
        // Same sub-format as the sample ("Card limit") — the auto-derived pattern keeps that
        // wording as literal anchor, so only same-format messages count.
        smsRepo.upsert(smsMessage(id = "m1", body = "You spent AED 6.50 at YANGO. Your available Tabby Card limit is now AED 59.39."))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onIntent(SmsTemplateEditIntent.SampleChanged(tabby))
        advanceUntilIdle()

        assertEquals(1, vm.state.value.inboxPreview?.matches)
    }
}
