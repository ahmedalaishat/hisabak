package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.sms.domain.ParsedSmsData
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.testutil.FakeAiSmsParser
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.FakeSmsRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.aed
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

class SuggestAiParseUseCaseTest {

    private val clock = TestClock()
    private val smsRepo = FakeSmsRepository()
    private val brandRepo = FakeBrandRepository()
    private val aiParser = FakeAiSmsParser()
    private val analytics = FakeAnalytics()

    private val suggest = SuggestAiParseUseCase(aiParser, smsRepo, brandRepo, Currency.AED, clock, analytics)

    private val receivedAt = Instant.parse("2026-03-04T09:30:00Z")

    private fun brand(name: String) = Brand(id = BrandId.new(), name = name, categoryId = null)

    private suspend fun storedMessage(
        transactionId: TransactionId? = null,
        parsed: ParsedSmsData? = null,
    ): SmsMessage {
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = "Your card was charged 12.50 at Noon",
            receivedAt = receivedAt,
            transactionId = transactionId,
            parsed = parsed,
        )
        smsRepo.upsert(message)
        return message
    }

    @Test
    fun `a complete model result is stored as a suggestion`() = runTest {
        aiParser.result = AiParsedSms("Noon", 12_50, "AED", null)
        val message = storedMessage()

        val result = suggest(message.id, source = "auto")

        assertTrue(result is DomainResult.Success)
        val stored = smsRepo.current.single()
        assertEquals("Noon", stored.suggested?.brandName)
        assertEquals(aed(12_50), stored.suggested?.amount)
        assertNull(stored.parsed)
        assertNull(stored.transactionId)
        assertEquals(listOf("ai_parse_attempted", "ai_parse_succeeded"), analytics.names())
    }

    @Test
    fun `an unavailable parser is never invoked`() = runTest {
        aiParser.availability = AiParserAvailability.Unavailable
        val message = storedMessage()

        val result = suggest(message.id, source = "auto")

        assertTrue(result is DomainResult.Failure)
        assertTrue(aiParser.parsedBodies.isEmpty())
        assertEquals(listOf("ai_parse_failed"), analytics.names())
    }

    @Test
    fun `a linked message is left alone`() = runTest {
        val message = storedMessage(transactionId = TransactionId.new())

        val result = suggest(message.id, source = "manual")

        assertTrue(result is DomainResult.Failure)
        assertTrue(aiParser.parsedBodies.isEmpty())
    }

    @Test
    fun `an empty model result stores nothing`() = runTest {
        aiParser.result = null
        val message = storedMessage()

        val result = suggest(message.id, source = "manual")

        assertTrue(result is DomainResult.Failure)
        assertNull(smsRepo.current.single().suggested)
        assertEquals(listOf("ai_parse_attempted", "ai_parse_failed"), analytics.names())
    }

    @Test
    fun `a result missing the amount stores nothing`() = runTest {
        aiParser.result = AiParsedSms("Noon", null, "AED", null)
        val message = storedMessage()

        val result = suggest(message.id, source = "auto")

        assertTrue(result is DomainResult.Failure)
        assertNull(smsRepo.current.single().suggested)
    }

    @Test
    fun `a result with a blank brand stores nothing`() = runTest {
        aiParser.result = AiParsedSms("  ", 12_50, "AED", null)
        val message = storedMessage()

        assertTrue(suggest(message.id, source = "auto") is DomainResult.Failure)
        assertNull(smsRepo.current.single().suggested)
    }

    @Test
    fun `a missing date falls back to when the sms arrived`() = runTest {
        aiParser.result = AiParsedSms("Noon", 12_50, null, null)
        val message = storedMessage()

        suggest(message.id, source = "auto")

        val suggested = smsRepo.current.single().suggested
        assertEquals(receivedAt, suggested?.occurredAt)
        assertEquals(Currency.AED, suggested?.amount?.currency)
    }

    @Test
    fun `a non-iso model currency falls back to the app currency`() = runTest {
        // Observed on device: a local abbreviation ("Dhs.") aborted Currency's validation.
        aiParser.result = AiParsedSms("Noon", 12_50, "Dhs.", null)
        val message = storedMessage()

        val result = suggest(message.id, source = "auto")

        assertTrue(result is DomainResult.Success)
        assertEquals(Currency.AED, smsRepo.current.single().suggested?.amount?.currency)
    }

    @Test
    fun `a valid lowercase model currency is honored`() = runTest {
        aiParser.result = AiParsedSms("Noon", 12_50, "usd", null)
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals(Currency.USD, smsRepo.current.single().suggested?.amount?.currency)
    }

    @Test
    fun `known brands are passed to the model most used first`() = runTest {
        aiParser.result = AiParsedSms("Noon", 12_50, "AED", null)
        brandRepo.emit(listOf(brand("Carrefour"), brand("Talabat")))
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals(listOf("Carrefour", "Talabat"), aiParser.lastKnownBrands)
    }

    @Test
    fun `a brand differing only in case snaps to the existing name`() = runTest {
        aiParser.result = AiParsedSms("NOON", 12_50, "AED", null)
        brandRepo.emit(listOf(brand("Noon")))
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals("Noon", smsRepo.current.single().suggested?.brandName)
    }

    @Test
    fun `a merchant string containing an existing brand snaps to it`() = runTest {
        aiParser.result = AiParsedSms("CARREFOUR DIFC", 12_50, "AED", null)
        brandRepo.emit(listOf(brand("Carrefour")))
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals("Carrefour", smsRepo.current.single().suggested?.brandName)
    }

    @Test
    fun `a small typo snaps to the existing brand`() = runTest {
        aiParser.result = AiParsedSms("Carefour", 12_50, "AED", null)
        brandRepo.emit(listOf(brand("Carrefour")))
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals("Carrefour", smsRepo.current.single().suggested?.brandName)
    }

    @Test
    fun `an unrelated merchant keeps the model string`() = runTest {
        aiParser.result = AiParsedSms("Amazon", 12_50, "AED", null)
        brandRepo.emit(listOf(brand("Carrefour"), brand("Noon")))
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals("Amazon", smsRepo.current.single().suggested?.brandName)
    }

    @Test
    fun `a far future model date is replaced with the arrival time`() = runTest {
        val hallucinated = (clock.now() + 30.days).toEpochMilliseconds()
        aiParser.result = AiParsedSms("Noon", 12_50, "AED", hallucinated)
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals(receivedAt, smsRepo.current.single().suggested?.occurredAt)
    }

    @Test
    fun `a hallucinated past date is replaced with the arrival time`() = runTest {
        // Observed on device: a dateless body ("400 lulu") provoked a date months in the past.
        val hallucinated = (receivedAt - 30.days).toEpochMilliseconds()
        aiParser.result = AiParsedSms("Lulu", 400_00, "AED", hallucinated)
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals(receivedAt, smsRepo.current.single().suggested?.occurredAt)
    }

    @Test
    fun `a model date shortly before arrival is trusted`() = runTest {
        val plausible = receivedAt - 1.days
        aiParser.result = AiParsedSms("Noon", 12_50, "AED", plausible.toEpochMilliseconds())
        val message = storedMessage()

        suggest(message.id, source = "auto")

        assertEquals(plausible, smsRepo.current.single().suggested?.occurredAt)
    }
}
