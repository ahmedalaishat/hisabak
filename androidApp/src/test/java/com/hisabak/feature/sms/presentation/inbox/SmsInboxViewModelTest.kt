package com.hisabak.feature.sms.presentation.inbox

import com.hisabak.core.common.Currency
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.usecase.DeleteSmsUseCase
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import com.hisabak.feature.sms.domain.usecase.ObserveSmsMessagesUseCase
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeCategoryLimitAlertDao
import com.hisabak.testutil.FakeCategoryLimitRepository
import com.hisabak.testutil.FakeCategoryRepository
import com.hisabak.testutil.FakeNotificationRepository
import com.hisabak.testutil.FakeNotificationStrings
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.MainDispatcherRule
import com.hisabak.testutil.RecordingNotifier
import com.hisabak.testutil.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class SmsInboxViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val notifier = RecordingNotifier()
    private val detector = RegexSmsTemplateDetector(listOf("Purchase of AED {amount} at {brand} done"))
    private val parser = TemplateSmsParser(Currency.AED, TimeZone.UTC)

    private val ingest = IngestSmsUseCase(
        smsRepository = smsRepo,
        processor = SmsTransactionProcessor(
            detector = detector,
            parser = parser,
            findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
            transactionRepository = transactionRepo,
            smsRepository = smsRepo,
            clock = clock,
        ),
        clock = clock,
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
        limitMonitor = CategoryLimitMonitor(
            transactions = transactionRepo,
            brands = brandRepo,
            categories = FakeCategoryRepository(),
            limits = FakeCategoryLimitRepository(),
            notifications = FakeNotificationRepository(),
            alertDao = FakeCategoryLimitAlertDao(),
            systemNotifier = notifier,
            currency = Currency.AED,
            clock = clock,
            strings = FakeNotificationStrings(),
        ),
        analytics = FakeAnalytics(),
    )

    private fun viewModel() = SmsInboxViewModel(
        observeMessages = ObserveSmsMessagesUseCase(smsRepo),
        capture = capture,
        deleteSms = DeleteSmsUseCase(smsRepo),
        detector = detector,
        parser = parser,
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
}
