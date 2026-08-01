package com.hisabak.feature.sms.domain

import com.hisabak.testutil.parserTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

class SmsTemplateRankingTest {

    @Test
    fun `more literal anchor text ranks first regardless of input order`() {
        val generic = parserTemplate(id = "generic", pattern = "{amount} at {brand}")
        val specific = parserTemplate(
            id = "specific",
            pattern = "You spent AED {amount} at {brand}. Your available Tabby",
        )

        assertEquals(listOf("specific", "generic"), rankTemplates(listOf(generic, specific)).map { it.id.value })
    }

    @Test
    fun `user templates beat defaults on equal anchors`() {
        val default = parserTemplate(id = "d", pattern = "Payment of AED {amount}", isDefault = true)
        val user = parserTemplate(id = "u", pattern = "Payment of AED {amount}", isDefault = false)

        assertEquals(listOf("u", "d"), rankTemplates(listOf(default, user)).map { it.id.value })
    }

    @Test
    fun `ties order deterministically regardless of input order`() {
        // Same anchor length, both defaults, same timestamp — the id breaks the tie, so a
        // REPLACE-style re-insert (enable toggle) can't reshuffle the list.
        val a = parserTemplate(id = "default-1", pattern = "Paid AED {amount} now", isDefault = true)
        val b = parserTemplate(id = "default-2", pattern = "Sent AED {amount} out", isDefault = true)

        assertEquals(
            rankTemplates(listOf(a, b)).map { it.id.value },
            rankTemplates(listOf(b, a)).map { it.id.value },
        )
    }

    @Test
    fun `anchor length ignores placeholders and whitespace`() {
        assertEquals(0, literalAnchorLength("{amount} {brand}"))
        assertEquals(2, literalAnchorLength("{amount} at {brand}"))
    }
}
