package com.hisabak.feature.dashboard.domain

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.effectiveFor
import kotlin.math.roundToLong
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth

/** A limit is a monthly cap; a calendar month is the one period where the cap and the period coincide. */
val SummaryPeriod.isSingleMonth: Boolean
    get() = this is SummaryPeriod.Month

/**
 * The limit budget for [from, toExclusive): each month's cap, prorated by the share of its days the
 * span covers. A whole month gets its cap, a whole year the twelve caps summed, and a 90-day window
 * the pro-rata slice of each month it touches — so a window never carries one month's cap against
 * three months of spend, nor a whole month's cap against a single week. Null if no cap applies.
 */
fun limitBetween(
    limits: List<CategoryLimit>,
    categoryId: CategoryId,
    from: LocalDate,
    toExclusive: LocalDate,
): Long? {
    var any = false
    var total = 0.0
    var month = from.yearMonth
    while (month.firstDay < toExclusive) {
        val cap = limits.effectiveFor(categoryId, month)?.amountMinor
        val monthEnd = month.plus(1, DateTimeUnit.MONTH).firstDay
        val days = maxOf(month.firstDay, from).daysUntil(minOf(monthEnd, toExclusive))
        if (cap != null && days > 0) {
            any = true
            total += cap.toDouble() * days / month.numberOfDays
        }
        month = month.plus(1, DateTimeUnit.MONTH)
    }
    return if (any) total.roundToLong() else null
}
