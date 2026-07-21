package com.hisabak.feature.sms.domain

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import com.hisabak.testutil.smsMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class SmsTransactionProcessorTest {

    private val clock = TestClock()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()
    private val smsRepo = FakeSmsRepository()

    private fun processor(patterns: List<String>) = SmsTransactionProcessor(
        detector = RegexSmsTemplateDetector(patterns),
        parser = TemplateSmsParser(defaultCurrency = Currency.AED, zone = ZoneOffset.UTC),
        findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
        transactionRepository = transactionRepo,
        smsRepository = smsRepo,
        clock = clock,
    )

    @Test
    fun `happy path creates a brand, a linked transaction, and links the sms`() = runTest {
        val processor = processor(listOf("Purchase of AED {amount} at {brand} on {date} end"))
        val message = smsMessage(id = "s1", body = "Purchase of AED 75.50 at Lulu on 17-06-2026 end")

        val result = processor.process(message)

        assertTrue(result is DomainResult.Success)
        val tx = (result as DomainResult.Success).value
        assertEquals(aed(75_50), tx.amount)
        assertEquals("s1", tx.sourceSmsId)
        assertEquals(Instant.parse("2026-06-17T00:00:00Z"), tx.occurredAt)

        val brand = brandRepo.current.single()
        assertEquals("Lulu", brand.name)
        assertEquals(brand.id, tx.brandId)

        assertEquals(tx, transactionRepo.current.single())
        val linked = smsRepo.current.single()
        assertEquals(tx.id, linked.transactionId)
        assertNotNull(linked.parsed)
    }

    @Test
    fun `no matching template fails validation`() = runTest {
        val processor = processor(listOf("Purchase of AED {amount} at {brand} end"))

        val result = processor.process(smsMessage(body = "random unrelated text"))

        assertTrue(result is DomainResult.Failure)
        assertTrue((result as DomainResult.Failure).error is DomainError.ValidationFailed)
        assertTrue(transactionRepo.current.isEmpty())
    }

    @Test
    fun `missing brand fails validation`() = runTest {
        val processor = processor(listOf("Spent AED {amount} today"))

        val result = processor.process(smsMessage(body = "Spent AED 20 today"))

        val error = (result as DomainResult.Failure).error as DomainError.ValidationFailed
        assertTrue(error.message.contains("brand", ignoreCase = true))
    }

    @Test
    fun `missing amount fails validation`() = runTest {
        val processor = processor(listOf("Purchase at {brand} completed"))

        val result = processor.process(smsMessage(body = "Purchase at Carrefour completed"))

        val error = (result as DomainResult.Failure).error as DomainError.ValidationFailed
        assertTrue(error.message.contains("amount", ignoreCase = true))
    }

    @Test
    fun `falls back to defaultDate when the sms has no date`() = runTest {
        val processor = processor(listOf("Purchase of AED {amount} at {brand} done"))
        val fallback = Instant.parse("2026-01-02T03:04:05Z")

        val result = processor.process(
            smsMessage(body = "Purchase of AED 12.00 at Noon done"),
            defaultDate = fallback,
        )

        val tx = (result as DomainResult.Success).value
        assertEquals(fallback, tx.occurredAt)
    }

    @Test
    fun `reuses an existing brand instead of creating a duplicate`() = runTest {
        val processor = processor(listOf("Purchase of AED {amount} at {brand} done"))
        processor.process(smsMessage(id = "s1", body = "Purchase of AED 10.00 at Lulu done"))

        processor.process(smsMessage(id = "s2", body = "Purchase of AED 20.00 at Lulu done"))

        assertEquals(1, brandRepo.current.size)
        assertEquals(2, transactionRepo.current.size)
    }
}
