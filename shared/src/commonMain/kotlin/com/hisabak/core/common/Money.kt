package com.hisabak.core.common

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

        /** Exact major → minor conversion from a plain decimal string. Money never round-trips
         *  through [Double], so cents are preserved at any magnitude; half-up rounding handles
         *  sub-cent inputs. Throws on input that isn't a finite, in-range decimal. */
        fun ofMajor(amount: String, currency: Currency): Money =
            Money(
                requireNotNull(parseMinor(amount)) { "Invalid amount: $amount" },
                currency,
            )

        fun ofMajor(amount: Double, currency: Currency): Money {
            require(amount.isFinite()) { "Invalid amount: $amount" }
            return ofMajor(plainDecimalString(amount), currency)
        }

        /** Parses a normalized major-unit amount string (digits + a single `.`) into [Money], or
         *  null if it isn't a finite, in-range number. Callers normalize separators first
         *  (see `sanitizeAmountInput` for user input; the SMS parser strips bank-format grouping). */
        fun parseMajor(raw: String, currency: Currency): Money? {
            val minor = parseMinor(raw.trim().trimEnd('.')) ?: return null
            return Money(minor, currency)
        }

        /** Pure decimal-string → minor-units parser (no BigDecimal, so it's multiplatform-safe).
         *  Exact at any magnitude that fits a Long; rounds sub-cent digits half up (away from
         *  zero), matching the previous BigDecimal HALF_UP behavior. */
        private fun parseMinor(raw: String): Long? {
            var s = raw
            val negative = s.startsWith("-")
            if (negative || s.startsWith("+")) s = s.substring(1)
            if (s.isEmpty()) return null
            val dot = s.indexOf('.')
            val intPart = if (dot >= 0) s.substring(0, dot) else s
            val fracPart = if (dot >= 0) s.substring(dot + 1) else ""
            if (intPart.isEmpty() && fracPart.isEmpty()) return null
            if (!intPart.all { it in '0'..'9' } || !fracPart.all { it in '0'..'9' }) return null
            val cents = fracPart.take(2).padEnd(2, '0')
            var minor = (intPart.trimStart('0') + cents).toLongOrNull() ?: return null
            val roundUp = fracPart.drop(2).firstOrNull()?.let { it >= '5' } == true
            if (roundUp) {
                if (minor == Long.MAX_VALUE) return null
                minor += 1
            }
            return if (negative) -minor else minor
        }

        /** Shortest round-trip decimal for [value] with any exponent notation expanded, matching
         *  `BigDecimal.valueOf(double)` semantics without the JVM-only type. */
        private fun plainDecimalString(value: Double): String {
            val s = value.toString()
            val e = s.indexOfFirst { it == 'e' || it == 'E' }
            if (e < 0) return s
            val exp = s.substring(e + 1).toInt()
            var mantissa = s.substring(0, e)
            val negative = mantissa.startsWith("-")
            if (negative) mantissa = mantissa.substring(1)
            val dot = mantissa.indexOf('.')
            val digits = mantissa.replace(".", "")
            val pointIndex = (if (dot < 0) mantissa.length else dot) + exp
            val plain = when {
                pointIndex <= 0 -> "0." + "0".repeat(-pointIndex) + digits
                pointIndex >= digits.length -> digits + "0".repeat(pointIndex - digits.length)
                else -> digits.take(pointIndex) + "." + digits.drop(pointIndex)
            }
            return if (negative) "-$plain" else plain
        }
    }
}
