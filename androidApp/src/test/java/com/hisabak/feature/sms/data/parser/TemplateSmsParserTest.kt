package com.hisabak.feature.sms.data.parser

import com.hisabak.core.common.Currency
import com.hisabak.feature.sms.domain.SmsTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class TemplateSmsParserTest {

    private val parser = TemplateSmsParser(defaultCurrency = Currency.AED, zone = ZoneOffset.UTC)

    private fun parse(fields: Map<String, String>) =
        parser.parse(body = "", template = SmsTemplate(pattern = "x", fields = fields))

    @Test
    fun `strips thousands separators from the amount`() {
        val parsed = parse(mapOf("amount" to "1,234.50"))
        assertEquals(123_450L, parsed.amount?.amountMinor)
        assertEquals(Currency.AED, parsed.amount?.currency)
    }

    @Test
    fun `unparseable amount yields null`() {
        assertNull(parse(mapOf("amount" to "N/A")).amount)
    }

    @Test
    fun `trims the brand and treats blank as null`() {
        assertEquals("Lulu", parse(mapOf("brand" to "  Lulu  ")).brandName)
        assertNull(parse(mapOf("brand" to "   ")).brandName)
    }

    @Test
    fun `parses day-month-year dates`() {
        val parsed = parse(mapOf("date" to "17-06-2026", "time" to "14:30"))
        assertEquals(Instant.parse("2026-06-17T14:30:00Z"), parsed.occurredAt)
    }

    @Test
    fun `parses iso dates`() {
        val parsed = parse(mapOf("date" to "2026-06-17", "time" to "14:30:45"))
        assertEquals(Instant.parse("2026-06-17T14:30:45Z"), parsed.occurredAt)
    }

    @Test
    fun `normalises slash separated dates`() {
        val parsed = parse(mapOf("date" to "17/06/2026"))
        assertEquals(Instant.parse("2026-06-17T00:00:00Z"), parsed.occurredAt)
    }

    @Test
    fun `parses twelve-hour time with meridiem`() {
        val parsed = parse(mapOf("date" to "17-06-2026", "time" to "2:30 PM"))
        assertEquals(Instant.parse("2026-06-17T14:30:00Z"), parsed.occurredAt)
    }

    @Test
    fun `defaults to midnight when time is absent`() {
        val parsed = parse(mapOf("date" to "17-06-2026"))
        assertEquals(Instant.parse("2026-06-17T00:00:00Z"), parsed.occurredAt)
    }

    @Test
    fun `falls back to the datetime field when date is missing`() {
        val parsed = parse(mapOf("datetime" to "17-06-2026", "time" to "08:00"))
        assertEquals(Instant.parse("2026-06-17T08:00:00Z"), parsed.occurredAt)
    }

    @Test
    fun `missing or blank date yields null occurredAt`() {
        assertNull(parse(mapOf("amount" to "10")).occurredAt)
        assertNull(parse(mapOf("date" to "   ")).occurredAt)
    }

    @Test
    fun `unparseable date yields null occurredAt`() {
        assertNull(parse(mapOf("date" to "tomorrow")).occurredAt)
    }
}
