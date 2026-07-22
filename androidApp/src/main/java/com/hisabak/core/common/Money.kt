package com.hisabak.core.common

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

data class Money(
    val amountMinor: Long,
    val currency: Currency,
) : Comparable<Money> {

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = amountMinor + other.amountMinor)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(amountMinor = amountMinor - other.amountMinor)
    }

    operator fun unaryMinus(): Money = copy(amountMinor = -amountMinor)

    operator fun times(scalar: Int): Money = copy(amountMinor = amountMinor * scalar)

    fun abs(): Money = copy(amountMinor = abs(amountMinor))

    val isZero: Boolean get() = amountMinor == 0L
    val isPositive: Boolean get() = amountMinor > 0L
    val isNegative: Boolean get() = amountMinor < 0L

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: $currency vs ${other.currency}"
        }
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0L, currency)

        /** Exact major → minor conversion. Money never round-trips through [Double], so cents are
         *  preserved at any magnitude; half-up rounding handles sub-cent inputs. */
        fun ofMajor(amount: BigDecimal, currency: Currency): Money =
            Money(amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact(), currency)

        fun ofMajor(amount: Double, currency: Currency): Money =
            ofMajor(BigDecimal.valueOf(amount), currency)

        /** Parses a normalized major-unit amount string (digits + a single `.`) into [Money], or
         *  null if it isn't a finite, in-range number. Callers normalize separators first
         *  (see `sanitizeAmountInput` for user input; the SMS parser strips bank-format grouping). */
        fun parseMajor(raw: String, currency: Currency): Money? {
            val amount = raw.trim().trimEnd('.').toBigDecimalOrNull() ?: return null
            return runCatching { ofMajor(amount, currency) }.getOrNull()
        }
    }
}
