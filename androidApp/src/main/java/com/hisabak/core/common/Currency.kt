package com.hisabak.core.common

@JvmInline
value class Currency(val code: String) {
    init {
        require(code.length == 3 && code.all { it.isUpperCase() }) {
            "Currency must be a 3-letter ISO-4217 code in uppercase, got: $code"
        }
    }

    companion object {
        val USD = Currency("USD")
        val EUR = Currency("EUR")
        val SAR = Currency("SAR")
        val AED = Currency("AED")
        val EGP = Currency("EGP")
        val GBP = Currency("GBP")
    }
}
