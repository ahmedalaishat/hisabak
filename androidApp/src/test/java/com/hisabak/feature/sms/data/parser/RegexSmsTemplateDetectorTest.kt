package com.hisabak.feature.sms.data.parser

import com.hisabak.feature.sms.domain.DefaultSmsTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegexSmsTemplateDetectorTest {

    // The detector masks `{x}` as non-greedy `(.*?)` and matches the surrounding text literally,
    // so a placeholder needs literal text after it to capture anything, and template punctuation
    // (e.g. a trailing `.`) is a real dot that bounds the final field.

    @Test
    fun `extracts placeholder fields bounded by literals`() {
        val detector = RegexSmsTemplateDetector(listOf("Payment of AED {amount} to {brand} on card"))

        val template = detector.detect("Payment of AED 150.00 to Carrefour on card")

        assertEquals("150.00", template?.fields?.get("amount"))
        assertEquals("Carrefour", template?.fields?.get("brand"))
    }

    @Test
    fun `returns null when no template matches`() {
        val detector = RegexSmsTemplateDetector(listOf("Payment of AED {amount} to {brand} now"))

        assertNull(detector.detect("Totally unrelated message"))
    }

    @Test
    fun `first matching template wins`() {
        val detector = RegexSmsTemplateDetector(
            listOf(
                "AED {amount} at {brand} done",
                "AED {amount} at {other} done",
            ),
        )

        val template = detector.detect("AED 10 at Shop done")

        assertEquals("AED {amount} at {brand} done", template?.pattern)
        assertEquals("Shop", template?.fields?.get("brand"))
    }

    @Test
    fun `ignore placeholders are dropped from fields`() {
        val detector = RegexSmsTemplateDetector(
            listOf("Your Cr.Card {card} was used for AED{amount} on {date} at {brand},{ignore} ref"),
        )

        val template = detector.detect("Your Cr.Card 1234 was used for AED50 on 17-06-2026 at Spinneys,99 ref")

        assertEquals("50", template?.fields?.get("amount"))
        assertEquals("Spinneys", template?.fields?.get("brand"))
        assertEquals(false, template?.fields?.containsKey("ignore"))
    }

    @Test
    fun `matching is case insensitive`() {
        val detector = RegexSmsTemplateDetector(listOf("Payment of AED {amount} to {brand} now"))

        val template = detector.detect("PAYMENT OF AED 99 TO ADNOC now")

        assertEquals("99", template?.fields?.get("amount"))
        assertEquals("ADNOC", template?.fields?.get("brand"))
    }

    @Test
    fun `real default debit template captures amount brand date and time`() {
        val detector = RegexSmsTemplateDetector(DefaultSmsTemplates.patterns)

        val template = detector.detect(
            "AED 250.75 has been debited from 1234 using card 9 at Lulu on 17-06-2026 14:30.",
        )

        assertEquals("250.75", template?.fields?.get("amount"))
        assertEquals("Lulu", template?.fields?.get("brand"))
        assertEquals("17-06-2026", template?.fields?.get("date"))
        // Time is bounded by the literal trailing dot, so it is no longer swallowed.
        assertEquals("14:30", template?.fields?.get("time"))
    }

    @Test
    fun `dots in templates match literally, not as wildcards`() {
        val detector = RegexSmsTemplateDetector(listOf("Ref {id}.END"))

        assertEquals("42", detector.detect("Ref 42.END")?.fields?.get("id"))
        assertNull(detector.detect("Ref 42XEND"))
    }
}
