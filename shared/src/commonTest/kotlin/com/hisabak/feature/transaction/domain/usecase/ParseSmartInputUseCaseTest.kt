package com.hisabak.feature.transaction.domain.usecase

import com.hisabak.core.common.Currency
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.sms.domain.ai.AiParsedSms
import com.hisabak.feature.sms.domain.ai.AiParserAvailability
import com.hisabak.testutil.FakeAiSmsParser
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeBrandRepository
import com.hisabak.testutil.TestClock
import com.hisabak.testutil.brand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.runTest

class ParseSmartInputUseCaseTest {

    private val clock = TestClock()
    private val aiParser = FakeAiSmsParser()
    private val analytics = FakeAnalytics()
    private val brandRepo = FakeBrandRepository(
        listOf(brand(id = "b-noon", name = "Noon"), brand(id = "b-lulu", name = "Lulu")),
    )

    private fun useCase() = ParseSmartInputUseCase(
        aiParser = aiParser,
        brandRepository = brandRepo,
        defaultCurrency = Currency.AED,
        clock = clock,
        analytics = analytics,
    )

    private fun success(result: DomainResult<ParseSmartInputUseCase.SmartParse>) =
        (result as DomainResult.Success).value

    @Test
    fun `a known brand resolves to its id with the canonical name`() = runTest {
        aiParser.freeTextResult = AiParsedSms("noon", 10_000L, null, null)

        val parse = success(useCase()("100 at noon"))

        assertEquals(BrandId("b-noon"), parse.brandId)
        assertEquals("Noon", parse.brandName)
        assertEquals(10_000L, parse.amount.amountMinor)
        assertEquals(Currency.AED, parse.amount.currency)
    }

    @Test
    fun `an unknown brand yields a null id and keeps the name for creation`() = runTest {
        aiParser.freeTextResult = AiParsedSms("Hardees", 3_500L, null, null)

        val parse = success(useCase()("hardees 35"))

        assertNull(parse.brandId)
        assertEquals("Hardees", parse.brandName)
    }

    @Test
    fun `a containment-only match outside the brand hints is not silently linked`() = runTest {
        // "Noon Food" is beyond the usage-top brand hints, so canonicalize can't snap "Noon" to
        // it; the DB lookup then finds it by containment — but linking a brand other than the one
        // shown would be a lie, so the id stays null and the sheet offers creation instead.
        val fillers = (1..50).map { brand(id = "b-$it", name = "Filler$it") }
        val repo = FakeBrandRepository(fillers + brand(id = "b-nf", name = "Noon Food"))
        val useCase = ParseSmartInputUseCase(aiParser, repo, Currency.AED, clock, analytics)
        aiParser.freeTextResult = AiParsedSms("Noon", 10_000L, null, null)

        val parse = success(useCase("100 at noon"))

        assertNull(parse.brandId)
        assertEquals("Noon", parse.brandName)
    }

    @Test
    fun `an implausibly large amount is rejected as incomplete`() = runTest {
        aiParser.freeTextResult = AiParsedSms("Lulu", Long.MAX_VALUE / 2, null, null)

        assertTrue(useCase()("lulu 99999999999999999") is DomainResult.Failure)
    }

    @Test
    fun `a non-ISO currency falls back to the app currency`() = runTest {
        aiParser.freeTextResult = AiParsedSms("Lulu", 5_000L, "د.إ", null)

        val parse = success(useCase()("50 lulu"))

        assertEquals("AED", parse.amount.currency.code)
    }

    @Test
    fun `dates in the back window are kept, future and ancient dates fall back to now`() = runTest {
        val yesterday = clock.now - 1.days
        aiParser.freeTextResult = AiParsedSms("Lulu", 5_000L, null, yesterday.toEpochMilliseconds())
        assertEquals(yesterday, success(useCase()("lulu 50 yesterday")).occurredAt)

        val future = clock.now + 30.days
        aiParser.freeTextResult = AiParsedSms("Lulu", 5_000L, null, future.toEpochMilliseconds())
        assertEquals(clock.now, success(useCase()("lulu 50")).occurredAt)

        val ancient = clock.now - 400.days
        aiParser.freeTextResult = AiParsedSms("Lulu", 5_000L, null, ancient.toEpochMilliseconds())
        assertEquals(clock.now, success(useCase()("lulu 50")).occurredAt)
    }

    @Test
    fun `the prompt receives today's date for relative resolution`() = runTest {
        aiParser.freeTextResult = AiParsedSms("Lulu", 5_000L, null, null)

        useCase()("lulu 50 yesterday")

        // TestClock is 2026-06-17 (a Wednesday), UTC.
        assertEquals("2026-06-17, Wednesday", aiParser.lastTodayIso)
    }

    @Test
    fun `unavailable parser fails without calling the model`() = runTest {
        aiParser.availability = AiParserAvailability.Unavailable

        val result = useCase()("100 at noon")

        assertTrue(result is DomainResult.Failure)
        assertTrue(aiParser.parsedFreeTexts.isEmpty())
        assertEquals(listOf("smart_parse_failed"), analytics.names())
    }

    @Test
    fun `missing amount or brand fails as incomplete and fields never reach analytics`() = runTest {
        aiParser.freeTextResult = AiParsedSms(brandName = null, amountMinor = 10_000L, currencyCode = null, occurredAtEpochMillis = null)
        assertTrue(useCase()("just words") is DomainResult.Failure)

        aiParser.freeTextResult = AiParsedSms(brandName = "Lulu", amountMinor = null, currencyCode = null, occurredAtEpochMillis = null)
        assertTrue(useCase()("lulu") is DomainResult.Failure)

        // PII guard: neither the typed text nor a brand name may appear in any event param.
        assertTrue(
            analytics.logged.flatMap { it.params.values }.none { it == "just words" || it == "lulu" || it == "Lulu" },
        )
    }

    @Test
    fun `success logs the bucketed amount and brand-match flag only`() = runTest {
        aiParser.freeTextResult = AiParsedSms("noon", 10_000L, null, null)

        useCase()("100 at noon")

        assertEquals(listOf("smart_parse_attempted", "smart_parse_succeeded"), analytics.names())
        val event = analytics.logged.last()
        assertEquals(true, event.params["matched_brand"])
        assertTrue(event.params.values.none { it == "100 at noon" || it == "noon" || it == "Noon" })
    }
}
