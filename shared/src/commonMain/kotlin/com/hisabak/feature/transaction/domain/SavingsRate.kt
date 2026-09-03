package com.hisabak.feature.transaction.domain

/**
 * What share of the period's income survived it: `(income − expenses) / income`.
 *
 * Zero is break-even and the sign carries the meaning — a deficit is negative rather than merely
 * a smaller positive, which is the flaw in measuring income as a share of total money movement
 * (there, break-even lands at an arbitrary-looking 50% and overspending never reads as a loss).
 *
 * Savings and investment transfers are the caller's business to exclude: moving money into
 * savings is not spending it.
 *
 * Returns null when there is no income to measure against — a rate has no meaning before payday,
 * and reporting 0% or −100% would both be lies.
 */
fun savingsRate(incomeMinor: Long, expensesMinor: Long): Double? {
    if (incomeMinor <= 0L) return null
    return (incomeMinor - expensesMinor).toDouble() / incomeMinor.toDouble()
}
