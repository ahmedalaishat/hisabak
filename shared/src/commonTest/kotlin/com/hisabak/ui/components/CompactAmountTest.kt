package com.hisabak.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class CompactAmountTest {

    @Test
    fun `everyday amounts stay exact to two decimals`() {
        assertEquals("0.00", compactAmount(0.0, arabic = false))
        assertEquals("12.34", compactAmount(12.34, arabic = false))
        assertEquals("999.00", compactAmount(999.0, arabic = false))
        // The whole point of the 100K threshold: a salary or a rent payment keeps its digits.
        assertEquals("1,250.00", compactAmount(1_250.0, arabic = false))
        assertEquals("12,450.00", compactAmount(12_450.0, arabic = false))
        assertEquals("99,999.99", compactAmount(99_999.99, arabic = false))
    }

    @Test
    fun `six figures and up abbreviate to K with two decimals`() {
        assertEquals("100.00K", compactAmount(100_000.0, arabic = false))
        assertEquals("842.50K", compactAmount(842_500.0, arabic = false))
    }

    @Test
    fun `millions abbreviate to M with two decimals`() {
        assertEquals("1.00M", compactAmount(1_000_000.0, arabic = false))
        assertEquals("1.70M", compactAmount(1_700_000.0, arabic = false))
    }

    @Test
    fun `the dense threshold abbreviates from a thousand up`() {
        assertEquals("1.00K", compactAmount(1_000.0, arabic = false, threshold = COMPACT_THRESHOLD_DENSE))
        assertEquals("4.80K", compactAmount(4_800.0, arabic = false, threshold = COMPACT_THRESHOLD_DENSE))
        assertEquals("999.00", compactAmount(999.0, arabic = false, threshold = COMPACT_THRESHOLD_DENSE))
        assertEquals("1.70M", compactAmount(1_700_000.0, arabic = false, threshold = COMPACT_THRESHOLD_DENSE))
    }

    @Test
    fun `negatives keep their sign`() {
        assertEquals("-480.00K", compactAmount(-480_000.0, arabic = false))
        assertEquals("-4,800.00", compactAmount(-4_800.0, arabic = false))
    }

    @Test
    fun `minor-unit helper divides by 100 first`() {
        assertEquals("1,250.00", compactAmountMinor(125_000, arabic = false))
        assertEquals("3.42", compactAmountMinor(342, arabic = false))
    }

    @Test
    fun `rounds half up on the shortest decimal representation`() {
        // 12.345K — the binary double is just below .5 but its shortest repr says "12.345",
        // matching how java.util.Formatter (the previous implementation) rounded.
        assertEquals("12.35K", compactAmount(12_345.0, arabic = false, threshold = COMPACT_THRESHOLD_DENSE))
        assertEquals("1.23", compactAmount(1.234, arabic = false))
        assertEquals("999.99K", compactAmount(999_994.0, arabic = false))
        assertEquals("1,000.00K", compactAmount(999_995.0, arabic = false))
    }

    @Test
    fun `groups thousands in the formatted number`() {
        // 1,234,567,890 → 1,234.57M
        assertEquals("1,234.57M", compactAmount(1_234_567_890.0, arabic = false))
    }

    @Test
    fun `exact amount drops the abbreviation and keeps every digit`() {
        assertEquals("1,248,300.50", exactAmount(1_248_300.5, arabic = false))
        assertEquals("100,000.00", exactAmount(100_000.0, arabic = false))
        assertEquals("١٬٢٤٨٬٣٠٠٫٥٠", exactAmount(1_248_300.5, arabic = true))
    }

    @Test
    fun `arabic uses arabic-indic digits separators and suffixes`() {
        // Same output the previous "%,.2f" + ar-u-nu-arab locale produced (U+066C group,
        // U+066B decimal), plus the one-letter أ / م suffixes joined with a space.
        assertEquals("٤٫٨٠ أ", compactAmount(4_800.0, arabic = true, threshold = COMPACT_THRESHOLD_DENSE))
        assertEquals("١٫٧٠ م", compactAmount(1_700_000.0, arabic = true))
        assertEquals("١٢٫٣٤", compactAmount(12.34, arabic = true))
        assertEquals("١٬٢٣٤٫٥٧ م", compactAmount(1_234_567_890.0, arabic = true))
        assertEquals("-٤٨٠٫٠٠ أ", compactAmount(-480_000.0, arabic = true))
    }

    @Test
    fun `localizeDigits maps western digits only when arabic`() {
        assertEquals("٩+", localizeDigits("9+", arabic = true))
        assertEquals("٥٠٪", localizeDigits("50٪", arabic = true))
        assertEquals("9+", localizeDigits("9+", arabic = false))
    }
}
