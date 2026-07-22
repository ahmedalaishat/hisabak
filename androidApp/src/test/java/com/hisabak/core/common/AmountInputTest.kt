package com.hisabak.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AmountInputTest {

    @Test
    fun `keeps a plain decimal amount`() {
        assertEquals("1234.56", sanitizeAmountInput("1234.56"))
    }

    @Test
    fun `drops a thousands comma before the decimal point`() {
        assertEquals("1234.56", sanitizeAmountInput("1,234.56"))
    }

    @Test
    fun `treats the last separator as the decimal point`() {
        // comma-decimal locales: ',' is the decimal, '.' (if any) is grouping
        assertEquals("12.50", sanitizeAmountInput("12,50"))
        assertEquals("1234.56", sanitizeAmountInput("1.234,56"))
    }

    @Test
    fun `preserves a trailing separator so it does not vanish mid-typing`() {
        assertEquals("12.", sanitizeAmountInput("12."))
        assertEquals("12.", sanitizeAmountInput("12,"))
    }

    @Test
    fun `strips currency text and stray symbols`() {
        assertEquals("12.30", sanitizeAmountInput("AED 12.30 "))
    }

    @Test
    fun `is empty when there are no digits`() {
        assertEquals("", sanitizeAmountInput("abc"))
        assertEquals("", sanitizeAmountInput(""))
    }
}
