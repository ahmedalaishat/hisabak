package com.hisabak.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    private val aed = Currency.AED
    private fun money(minor: Long) = Money(minor, aed)

    @Test
    fun `plus adds amounts of the same currency`() {
        assertEquals(money(300), money(100) + money(200))
    }

    @Test
    fun `minus subtracts amounts of the same currency`() {
        assertEquals(money(-50), money(100) - money(150))
    }

    @Test
    fun `unaryMinus negates`() {
        assertEquals(money(-100), -money(100))
    }

    @Test
    fun `times multiplies by a scalar`() {
        assertEquals(money(600), money(200) * 3)
    }

    @Test
    fun `abs returns the magnitude`() {
        assertEquals(money(100), money(-100).abs())
        assertEquals(money(100), money(100).abs())
    }

    @Test
    fun `sign predicates reflect the amount`() {
        assertTrue(money(0).isZero)
        assertTrue(money(1).isPositive)
        assertTrue(money(-1).isNegative)
        assertFalse(money(0).isPositive)
        assertFalse(money(0).isNegative)
    }

    @Test
    fun `compareTo orders by minor amount`() {
        assertTrue(money(100) < money(200))
        assertTrue(money(200) > money(100))
        assertEquals(0, money(100).compareTo(money(100)))
    }

    @Test
    fun `ofMajor converts major units to minor`() {
        assertEquals(money(1234), Money.ofMajor(12.34, aed))
        assertEquals(money(0), Money.ofMajor(0.0, aed))
    }

    @Test
    fun `ofMajor rounds instead of truncating floating-point error`() {
        // (value * 100).toLong() would truncate these to 1998 / 6 / 434.
        assertEquals(money(1999), Money.ofMajor(19.99, aed))
        assertEquals(money(7), Money.ofMajor(0.07, aed))
        assertEquals(money(435), Money.ofMajor(4.35, aed))
        assertEquals(money(-1999), Money.ofMajor(-19.99, aed))
        assertEquals(money(100000000), Money.ofMajor(1_000_000.0, aed))
    }

    @Test
    fun `ofMajor from BigDecimal stays exact at magnitudes Double would corrupt`() {
        assertEquals(money(1_234_567_890_123L), Money.ofMajor("12345678901.23".toBigDecimal(), aed))
    }

    @Test
    fun `ofMajor rounds sub-cent BigDecimal input half up`() {
        assertEquals(money(1235), Money.ofMajor("12.345".toBigDecimal(), aed))
        assertEquals(money(1234), Money.ofMajor("12.344".toBigDecimal(), aed))
    }

    @Test
    fun `parseMajor reads normalized decimal strings`() {
        assertEquals(money(1250), Money.parseMajor("12.50", aed))
        assertEquals(money(1200), Money.parseMajor("12.", aed)) // trailing dot tolerated mid-typing
        assertEquals(money(50), Money.parseMajor(".5", aed))
    }

    @Test
    fun `parseMajor returns null for non-numbers and out-of-range values`() {
        assertNull(Money.parseMajor("", aed))
        assertNull(Money.parseMajor("abc", aed))
        assertNull(Money.parseMajor("99999999999999999999", aed)) // overflows Long minor units
    }

    @Test
    fun `zero is a zero amount in the given currency`() {
        assertEquals(money(0), Money.zero(aed))
    }

    @Test
    fun `arithmetic across currencies is rejected`() {
        val usd = Money(100, Currency.USD)
        val aedMoney = money(100)
        assertThrows(IllegalArgumentException::class.java) { aedMoney + usd }
        assertThrows(IllegalArgumentException::class.java) { aedMoney - usd }
        assertThrows(IllegalArgumentException::class.java) { aedMoney.compareTo(usd) }
    }
}
