package com.hisabak.feature.sms.domain.template

import com.hisabak.feature.sms.domain.DefaultSmsTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiTemplateSynthesisTest {

    private fun patternFor(body: String, brand: String, amountMinor: Long): String? =
        deriveAiSpans(body, brand, amountMinor)?.let { deriveTemplatePattern(body, it) }

    @Test
    fun `a learned pattern parses a second message of the same format`() {
        val first = "Purchase of AED 125.50 with card 1234 at CARREFOUR MALL on 12/08/2026. Balance AED 8,342.27."
        val pattern = assertNotNull(patternFor(first, "CARREFOUR MALL", 12_550))

        val second = "Purchase of AED 79.00 with card 9911 at LULU EXPRESS on 03/09/2026. Balance AED 1,204.10."
        val match = assertNotNull(matchTemplate(pattern, second))

        assertEquals("79.00", match.fields["amount"])
        assertEquals("LULU EXPRESS", match.fields["brand"])
    }

    @Test
    fun `every shipped bank format learns a rule that generalizes to another message`() {
        val first = mapOf(
            "{amount}" to "125.50", "{brand}" to "CARREFOUR MALL", "{card}" to "card 1234",
            "{account}" to "account 5678", "{date}" to "12/08/2026", "{time}" to "14:03:00",
            "{ignore}" to "REF99",
        )
        val second = mapOf(
            "{amount}" to "80.00", "{brand}" to "LULU", "{card}" to "card 9999",
            "{account}" to "account 4321", "{date}" to "03/09/2026", "{time}" to "09:12:00",
            "{ignore}" to "REF42",
        )

        for (pattern in DefaultSmsTemplates.patterns) {
            val a = first.entries.fold(pattern) { acc, (k, v) -> acc.replace(k, v) }
            val b = second.entries.fold(pattern) { acc, (k, v) -> acc.replace(k, v) }

            val learned = assertNotNull(patternFor(a, "CARREFOUR MALL", 12_550), "no rule for $pattern")
            val match = assertNotNull(matchTemplate(learned, b), "learned rule is message-specific: $learned")

            assertEquals("LULU", match.fields["brand"], "brand lost for $learned")
            assertEquals("80.00", match.fields["amount"], "amount lost for $learned")
        }
    }

    @Test
    fun `the balance figure becomes a placeholder rather than a literal`() {
        val body = "Payment of AED 40.00 to SPOTIFY done. Available balance AED 900.00"
        val pattern = assertNotNull(patternFor(body, "SPOTIFY", 4_000))

        assertTrue("900.00" !in pattern, "balance leaked into the pattern: $pattern")
        assertNotNull(matchTemplate(pattern, "Payment of AED 40.00 to SPOTIFY done. Available balance AED 12.34"))
    }

    @Test
    fun `an amount with thousands separators is located`() {
        val body = "AED 1,255.00 has been debited from your account at IKEA"
        val pattern = assertNotNull(patternFor(body, "IKEA", 125_500))
        val match = assertNotNull(matchTemplate(pattern, "AED 2,000.50 has been debited from your account at NOON"))

        assertEquals("2,000.50", match.fields["amount"])
        assertEquals("NOON", match.fields["brand"])
    }

    @Test
    fun `an amount written without decimals is located`() {
        val body = "Your card was used for AED 400 at ADNOC STATION today"
        val spans = assertNotNull(deriveAiSpans(body, "ADNOC STATION", 40_000))

        val amount = spans.single { it.role == TagRole.AMOUNT }
        assertEquals("400", body.substring(amount.start, amount.end))
    }

    @Test
    fun `the currency-marked amount wins over an identical figure elsewhere`() {
        val body = "Ref 50.00 processed. Purchase of AED 50.00 at DAISO completed"
        val spans = assertNotNull(deriveAiSpans(body, "DAISO", 5_000))

        val amount = spans.single { it.role == TagRole.AMOUNT }
        assertTrue(amount.start > body.indexOf("AED"), "picked the reference number, not the amount")
    }

    @Test
    fun `the brand is matched regardless of casing`() {
        val body = "Purchase of AED 20.00 at carrefour city completed"
        val spans = assertNotNull(deriveAiSpans(body, "Carrefour City", 2_000))

        val brand = spans.single { it.role == TagRole.BRAND }
        assertEquals("carrefour city", body.substring(brand.start, brand.end))
    }

    @Test
    fun `no template when the amount is absent from the body`() {
        assertNull(deriveAiSpans("Purchase at NOON completed successfully", "NOON", 12_550))
    }

    @Test
    fun `no template when the model cleaned the merchant beyond recognition`() {
        assertNull(deriveAiSpans("Purchase of AED 12.50 at CRF-MOE-4471 done", "Carrefour", 1_250))
    }

    @Test
    fun `no template when the amount and brand are the same span`() {
        assertNull(deriveAiSpans("Purchase of AED 400 completed", "400", 40_000))
    }
}
