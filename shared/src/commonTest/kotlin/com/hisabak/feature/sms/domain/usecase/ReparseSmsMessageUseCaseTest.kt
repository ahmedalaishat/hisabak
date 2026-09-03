package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.brand.domain.usecase.ResolveBrandUseCase
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeSmsTemplateRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.parserTemplate
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import com.hisabak.testutil.smsMessage
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.test.runTest

class ReparseSmsMessageUseCaseTest {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val txRepo = FakeTransactionRepository()
    private val brandRepo = FakeBrandRepository()
    private val tabby =
        "You spent AED 35.00 at HARDEES. Your available Tabby Card limit is now AED 8,342.27."

    private fun useCase(patterns: List<String>) = ReparseSmsMessageUseCase(
        smsRepository = smsRepo,
        templateRepository = FakeSmsTemplateRepository(
            patterns.mapIndexed { i, p -> parserTemplate(id = "tpl-$i", pattern = p) },
        ),
        parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
        // The processor's own detector is deliberately EMPTY: reparse must detect from the
        // template repository directly — going through the observing detector's async snapshot
        // is exactly the race that broke save-then-reparse on device.
        processor = SmsTransactionProcessor(
            detector = RegexSmsTemplateDetector(emptyList()),
            parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
            findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo, ResolveBrandUseCase(brandRepo)),
            transactionRepository = txRepo,
            smsRepository = smsRepo,
            clock = clock,
        ),
    )

    @Test
    fun `an unparsed message imports through the newly taught template`() = runTest {
        smsRepo.upsert(smsMessage(id = "s1", body = tabby))

        val result = useCase(listOf("You spent AED {amount} at {brand}. Your available Tabby"))(SmsMessageId("s1"))

        assertTrue(result is DomainResult.Success)
        val tx = txRepo.current.single()
        assertEquals(3_500L, tx.amount.amountMinor)
        val message = smsRepo.current.single()
        assertEquals(tx.id, message.transactionId)
        assertEquals("HARDEES", message.parsed?.brandName)
    }

    @Test
    fun `a pending AI suggestion is dropped when the template parse wins`() = runTest {
        smsRepo.upsert(
            smsMessage(id = "s1", body = tabby)
                .copy(suggested = ParsedSmsData("Hardees?", aed(3_400), clock.now())),
        )

        useCase(listOf("You spent AED {amount} at {brand}. Your available Tabby"))(SmsMessageId("s1"))

        val message = smsRepo.current.single()
        assertTrue(message.isLinked)
        // No stale suggestion left to masquerade as AI provenance on a template parse.
        assertNull(message.suggested)
    }

    @Test
    fun `no matching template leaves the message and its suggestion untouched`() = runTest {
        val suggestion = ParsedSmsData("Hardees", aed(3_500), clock.now())
        smsRepo.upsert(smsMessage(id = "s1", body = tabby).copy(suggested = suggestion))

        val result = useCase(listOf("Completely different {amount}"))(SmsMessageId("s1"))

        assertTrue(result is DomainResult.Failure)
        val message = smsRepo.current.single()
        assertNull(message.transactionId)
        assertEquals(suggestion, message.suggested)
        assertTrue(txRepo.current.isEmpty())
    }

    @Test
    fun `an already linked message is left alone`() = runTest {
        txRepo.emit(listOf(transaction(id = "t1", brandId = "b1")))
        smsRepo.upsert(
            smsMessage(id = "s1", body = tabby)
                .copy(transactionId = com.hisabak.feature.transaction.domain.TransactionId("t1")),
        )

        val result = useCase(listOf("You spent AED {amount} at {brand}. Your available Tabby"))(SmsMessageId("s1"))

        assertTrue(result is DomainResult.Success)
        assertEquals(1, txRepo.current.size) // no duplicate transaction
    }
}
