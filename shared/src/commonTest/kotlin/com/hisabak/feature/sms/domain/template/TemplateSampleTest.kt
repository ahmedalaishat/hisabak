package com.hisabak.feature.sms.domain.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TemplateSampleTest {

    private val tabby =
        "You spent AED 35.00 at HARDEES-WTC MALL. Your available Tabby Card limit is now AED 8,342.27."

    private fun spanOf(sample: String, text: String, role: TagRole): TagSpan {
        val start = sample.indexOf(text)
        require(start >= 0) { "'$text' not in sample" }
        return TagSpan(role, start, start + text.length)
    }

    @Test
    fun `deriving from the tagged Tabby sample produces a reusable pattern`() {
        val spans = listOf(
            spanOf(tabby, "35.00", TagRole.AMOUNT),
            spanOf(tabby, "HARDEES-WTC MALL", TagRole.BRAND),
            spanOf(tabby, "8,342.27", TagRole.SKIP),
        )

        val pattern = deriveTemplatePattern(tabby, spans)

        assertEquals(
            "You spent AED {amount} at {brand}. Your available Tabby Card limit is now AED {ignore}.",
            pattern,
        )
        // The same pattern parses a different message in the same format.
        val other = matchTemplate(pattern, "You spent AED 6.50 at YANGO. Your available Tabby Card limit is now AED 59.39.")
        assertNotNull(other)
        assertEquals("6.50", other.fields["amount"])
        assertEquals("YANGO", other.fields["brand"])
    }

    @Test
    fun `span trimming drops surrounding punctuation and whitespace`() {
        val sample = "Paid 12.00 to CAFE, DUBAI"
        val spans = listOf(
            spanOf(sample, "12.00", TagRole.AMOUNT),
            // Deliberately sloppy tag including the comma and trailing space.
            spanOf(sample, "CAFE, ", TagRole.BRAND),
        )

        assertEquals("Paid {amount} to {brand}, DUBAI", deriveTemplatePattern(sample, spans))
    }

    @Test
    fun `adjacent same-role spans merge into one placeholder`() {
        val sample = "Spent 9.00 at BURGER KING today"
        val spans = listOf(
            spanOf(sample, "9.00", TagRole.AMOUNT),
            spanOf(sample, "BURGER", TagRole.BRAND),
            spanOf(sample, "KING", TagRole.BRAND),
        )

        assertEquals("Spent {amount} at {brand} today", deriveTemplatePattern(sample, spans))
    }

    @Test
    fun `reconstructing spans from a stored pattern and sample round-trips the tags`() {
        val spans = listOf(
            spanOf(tabby, "35.00", TagRole.AMOUNT),
            spanOf(tabby, "HARDEES-WTC MALL", TagRole.BRAND),
            spanOf(tabby, "8,342.27", TagRole.SKIP),
        )
        val pattern = deriveTemplatePattern(tabby, spans)

        val rebuilt = reconstructSpans(pattern, tabby)

        assertNotNull(rebuilt)
        assertEquals(
            listOf(TagRole.AMOUNT to "35.00", TagRole.BRAND to "HARDEES-WTC MALL", TagRole.SKIP to "8,342.27"),
            rebuilt.map { it.role to tabby.substring(it.start, it.end) },
        )
    }

    @Test
    fun `reconstruction returns null when the pattern no longer matches`() {
        assertNull(reconstructSpans("Completely different {amount}", tabby))
    }

    @Test
    fun `suggestions tag the amount near the currency and skip the balance`() {
        val suggested = suggestSpans(tabby)

        val byRole = suggested.groupBy { it.role }
        assertEquals("35.00", tabby.substring(byRole.getValue(TagRole.AMOUNT).single().let { it.start }, byRole.getValue(TagRole.AMOUNT).single().end))
        // The trailing balance must not stay literal — it changes every message.
        assertTrue(byRole.getValue(TagRole.SKIP).any { tabby.substring(it.start, it.end) == "8,342.27" })
        // Brand guess: the words after " at ".
        assertTrue(byRole[TagRole.BRAND]?.any { tabby.substring(it.start, it.end).startsWith("HARDEES") } == true)
    }

    @Test
    fun `suggestions handle the multiline debit format with date and time`() {
        val body = "Debit Card Purchase\nCard XXXX7233\nAED 65.24\nTabby, 800 82229 DUBAI AE\n03/07/26 22:43\nBalance AED 4854.52"

        val byRole = suggestSpans(body).groupBy { it.role }

        assertEquals("65.24", body.substring(byRole.getValue(TagRole.AMOUNT).single().start, byRole.getValue(TagRole.AMOUNT).single().end))
        assertEquals("03/07/26", body.substring(byRole.getValue(TagRole.DATE).single().start, byRole.getValue(TagRole.DATE).single().end))
        assertEquals("22:43", body.substring(byRole.getValue(TagRole.TIME).single().start, byRole.getValue(TagRole.TIME).single().end))
    }

    @Test
    fun `a pattern ending in a placeholder extracts the same fields at runtime and in the preview`() {
        // Brand at the very end of the sample (no trailing punctuation) — the derived pattern
        // ends with {brand}; runtime find() and the editor's matchEntire preview must agree,
        // not silently capture empty at runtime.
        val sample = "You spent AED 35.00 at CARREFOUR MALL"
        val spans = listOf(
            spanOf(sample, "35.00", TagRole.AMOUNT),
            spanOf(sample, "CARREFOUR MALL", TagRole.BRAND),
        )
        val pattern = deriveTemplatePattern(sample, spans)
        assertTrue(pattern.endsWith("{brand}"))

        val runtime = matchTemplate(pattern, sample)?.fields
        val preview = previewFields(pattern, sample)?.fields

        assertEquals("CARREFOUR MALL", runtime?.get("brand"))
        assertEquals(runtime, preview)
    }

    @Test
    fun `previewFields extracts the tagged values from the sample`() {
        val pattern = "You spent AED {amount} at {brand}. Your available Tabby{ignore}"

        val fields = previewFields(pattern, tabby)?.fields

        assertEquals("35.00", fields?.get("amount"))
        assertEquals("HARDEES-WTC MALL", fields?.get("brand"))
    }
}
