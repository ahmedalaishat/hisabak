package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.transaction.domain.TransactionId
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
import com.hisabak.testutil.smsMessage
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.runTest

class ImportParsedSmsUseCaseTest {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val txRepo = FakeTransactionRepository()
    private val brandRepo = FakeBrandRepository()
    private val analytics = FakeAnalytics()

    private val useCase = ImportParsedSmsUseCase(
        smsRepository = smsRepo,
        processor = SmsTransactionProcessor(
            detector = RegexSmsTemplateDetector(emptyList()),
            parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
            findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
            transactionRepository = txRepo,
            smsRepository = smsRepo,
            clock = clock,
        ),
        limitMonitor = CategoryLimitMonitor(
            transactions = txRepo,
            brands = brandRepo,
            categories = FakeCategoryRepository(),
            limits = FakeCategoryLimitRepository(),
            notifications = FakeNotificationRepository(),
            alertStore = FakeCategoryLimitAlertStore(),
            systemNotifier = RecordingNotifier(),
            currency = Currency.AED,
            clock = clock,
            strings = FakeNotificationStrings(),
        ),
        analytics = analytics,
    )

    @Test
    fun `re-imports a parsed unlinked message from its stored parse`() = runTest {
        // The state a captured message returns to after its transaction is deleted.
        smsRepo.upsert(
            smsMessage(id = "s1", body = "Purchase of AED 42.00 at Lulu done")
                .copy(parsed = ParsedSmsData("Lulu", aed(42_00), clock.now())),
        )

        val result = useCase(SmsMessageId("s1"))

        assertTrue(result is DomainResult.Success)
        val tx = txRepo.current.single()
        assertEquals(42_00L, tx.amount.amountMinor)
        assertEquals(tx.id, smsRepo.current.single().transactionId)
        val event = analytics.logged.first { it.name == "sms_captured" }
        assertEquals("reimport", event.params["source"])
    }

    @Test
    fun `an already linked message is refused without a duplicate`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", brandId = "b1")))
        smsRepo.upsert(
            smsMessage(id = "s1", body = "Purchase of AED 42.00 at Lulu done")
                .copy(
                    parsed = ParsedSmsData("Lulu", aed(42_00), clock.now()),
                    transactionId = TransactionId("t1"),
                ),
        )

        val result = useCase(SmsMessageId("s1"))

        assertTrue(result is DomainResult.Failure)
        assertEquals(1, txRepo.current.size)
    }

    @Test
    fun `a message without a complete parse is refused`() = runTest {
        smsRepo.upsert(smsMessage(id = "s1", body = "mystery text"))

        val result = useCase(SmsMessageId("s1"))

        assertTrue(result is DomainResult.Failure)
        assertTrue(txRepo.current.isEmpty())
    }
}
