package com.hisabak.feature.dashboard.domain

import com.hisabak.core.common.SummaryPeriod

/** A limit is a monthly cap; these are the periods where the cap and the period coincide. */
val SummaryPeriod.isSingleMonth: Boolean
    get() = this == SummaryPeriod.CURRENT_MONTH || this == SummaryPeriod.LAST_MONTH

/**
 * The limit budget for [period] from a category's per-bucket limit series
 * (`DashboardSnapshot.limitByCategory`): the month's limit for a single-month window, or the sum of
 * each month's applicable limit for a multi-month one. Null if no limit applies.
 *
 * Shared by the Categories tab and the insights summary so the two can never disagree — a yearly
 * review that showed one month's cap against twelve months of spend read every category as over.
 */
fun periodLimit(limitSeries: List<Long?>, period: SummaryPeriod): Long? =
    if (period.isSingleMonth) {
        limitSeries.firstOrNull { it != null }
    } else {
        limitSeries.filterNotNull().takeIf { it.isNotEmpty() }?.sum()
    }
