package com.hisabak.feature.insights.domain

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import com.hisabak.feature.dashboard.domain.DashboardSnapshot
import com.hisabak.feature.dashboard.domain.periodLimit
import kotlin.math.roundToLong

/** One expense category's figures for the period. [priorMinor] is null when no prior period exists. */
data class CategorySpend(
    val id: CategoryId,
    val name: String,
    val color: String,
    val icon: String,
    val spentMinor: Long,
    val priorMinor: Long?,
    /** The period's limit budget — monthly cap for a month, the months' caps summed otherwise. */
    val limitMinor: Long?,
    val shareOfExpense: Double,
)

/**
 * Everything the review rules need, and nothing else — no transaction rows, no notes. This is
 * also the exact payload the AI layers will send, so its shape is the privacy boundary: a field
 * that isn't here cannot leave the phone.
 *
 * Derived from [DashboardSnapshot] rather than re-aggregated, so the review can never disagree
 * with the dashboard. [categories] is ordered by id so the summary is byte-stable for a given
 * ledger, which is what makes a cached prompt prefix possible later.
 */
data class InsightsSummary(
    val period: SummaryPeriod,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val priorIncomeMinor: Long?,
    val priorExpenseMinor: Long?,
    val categories: List<CategorySpend>,
    val uncategorizedMinor: Long,
    val uncategorizedCount: Int,
) {
    companion object {
        fun from(snapshot: DashboardSnapshot, period: SummaryPeriod): InsightsSummary {
            val expense = snapshot.expense.amountMinor
            val spentById = snapshot.expenseByCategory.associate { it.id to it.amount.amountMinor }
            // The snapshot reports a prior total of 0 for every category when there is no prior
            // period at all (ALL is the one period without a predecessor — see
            // SummaryPeriod.previousInstantRange). Passing that 0 through would make every
            // category "new spend"; null is the honest value.
            val hasPriorPeriod = period != SummaryPeriod.ALL
            val categories = snapshot.categoryOptions
                .filter { it.type == CategoryType.EXPENSES }
                .sortedBy { it.id.value }
                .map { option ->
                    val spent = spentById[option.id] ?: 0L
                    CategorySpend(
                        id = option.id,
                        name = option.name,
                        color = option.color,
                        icon = option.icon,
                        spentMinor = spent,
                        priorMinor = if (hasPriorPeriod) snapshot.trendPrevTotalByCategory[option.id] else null,
                        // The same budget the Categories tab shows: one month's cap for a month,
                        // the months' caps summed for a year — never one month's cap against a year.
                        limitMinor = periodLimit(snapshot.limitByCategory[option.id].orEmpty(), period),
                        shareOfExpense = if (expense > 0) spent.toDouble() / expense else 0.0,
                    )
                }
            return InsightsSummary(
                period = period,
                incomeMinor = snapshot.income.amountMinor,
                expenseMinor = expense,
                priorIncomeMinor = priorFromTrend(snapshot.income.amountMinor, snapshot.incomeTrendPct),
                priorExpenseMinor = priorFromTrend(expense, snapshot.expenseTrendPct),
                categories = categories,
                uncategorizedMinor = snapshot.uncategorizedTotal.amountMinor,
                uncategorizedCount = snapshot.uncategorizedCount,
            )
        }

        /**
         * The snapshot carries the period-over-period change as a percentage, not the prior total,
         * so the prior is recovered from it: `pct = (cur - prev) / prev` ⇒ `prev = cur / (1 + pct)`.
         * A null pct means the prior was zero or absent; a -100% change (spend fell to nothing)
         * is unrecoverable by this route and is treated as unknown rather than guessed.
         */
        private fun priorFromTrend(current: Long, trendPct: Double?): Long? {
            trendPct ?: return null
            val factor = 1 + trendPct / 100.0
            if (factor <= 0.0) return null
            return (current / factor).roundToLong()
        }
    }
}
