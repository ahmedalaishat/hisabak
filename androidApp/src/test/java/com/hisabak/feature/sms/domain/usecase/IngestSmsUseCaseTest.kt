package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.usecase.FindOrCreateBrandUseCase
import com.hisabak.feature.sms.data.parser.RegexSmsTemplateDetector
import com.hisabak.feature.sms.data.parser.TemplateSmsParser
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.FakeTransactionRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class IngestSmsUseCaseTest {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val transactionRepo = FakeTransactionRepository()

    private val processor = SmsTransactionProcessor(
        detector = RegexSmsTemplateDetector(listOf("Purchase of AED {amount} at {brand} done")),
        parser = TemplateSmsParser(Currency.AED, TimeZone.UTC),
        findOrCreateBrand = FindOrCreateBrandUseCase(brandRepo),
        transactionRepository = transactionRepo,
        smsRepository = smsRepo,
        clock = clock,
    )
    private val useCase = IngestSmsUseCase(smsRepo, processor, clock)

    @Test
    fun `persists the sms then produces a transaction`() = runTest {
        val result = useCase("Purchase of AED 42.00 at Lulu done")

        assertTrue(result is DomainResult.Success)
        assertEquals(aed(42_00), (result as DomainResult.Success).value.amount)
        assertEquals(1, smsRepo.current.size)
        assertEquals(1, transactionRepo.current.size)
        // The stored sms ends up linked to the created transaction.
        assertEquals(transactionRepo.current.single().id, smsRepo.current.single().transactionId)
    }

    @Test
    fun `an unrecognised sms is still stored but yields a failure`() = runTest {
        val result = useCase("not a bank message")

        assertTrue(result is DomainResult.Failure)
        assertEquals(1, smsRepo.current.size)
        assertTrue(transactionRepo.current.isEmpty())
    }

    @Test
    fun `a dateless sms is dated at the time it was received`() = runTest {
        val receivedAt = Instant.parse("2026-03-04T09:30:00Z")

        val result = useCase("Purchase of AED 42.00 at Lulu done", receivedAt)

        assertTrue(result is DomainResult.Success)
        assertEquals(receivedAt, (result as DomainResult.Success).value.occurredAt)
    }

    @Test
    fun `a redelivered sms with the same body and time is ignored`() = runTest {
        val receivedAt = Instant.parse("2026-03-04T09:30:00Z")
        useCase("Purchase of AED 42.00 at Lulu done", receivedAt)

        val second = useCase("Purchase of AED 42.00 at Lulu done", receivedAt)

        assertTrue(second is DomainResult.Failure)
        assertEquals(1, smsRepo.current.size)
        assertEquals(1, transactionRepo.current.size)
    }
}
