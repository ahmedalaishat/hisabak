package com.hisabak.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactAmountTest {

    @Test
    fun `under a thousand stays exact to two decimals`() {
        assertEquals("0.00", compactAmount(0.0))
        assertEquals("12.34", compactAmount(12.34))
        assertEquals("999.00", compactAmount(999.0))
    }

    @Test
    fun `thousands abbreviate to K with two decimals`() {
        assertEquals("1.00K", compactAmount(1_000.0))
        assertEquals("4.80K", compactAmount(4_800.0))
        assertEquals("842.50K", compactAmount(842_500.0))
    }

    @Test
    fun `millions abbreviate to M with two decimals`() {
        assertEquals("1.00M", compactAmount(1_000_000.0))
        assertEquals("1.70M", compactAmount(1_700_000.0))
    }

    @Test
    fun `negatives keep their sign`() {
        assertEquals("-4.80K", compactAmount(-4_800.0))
    }

    @Test
    fun `minor-unit helper divides by 100 first`() {
        assertEquals("1.25K", compactAmountMinor(125_000))
        assertEquals("3.42", compactAmountMinor(342))
    }
}
