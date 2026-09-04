package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.insights.domain.CategorySpend
import com.hisabak.feature.insights.domain.InsightsSummary
import kotlinx.datetime.LocalDate

internal fun spend(
    id: String,
    spent: Long,
    prior: Long? = null,
    limit: Long? = null,
    name: String = "Category $id",
) = CategorySpend(
    id = CategoryId(id),
    name = name,
    color = "blue",
    icon = "wallet",
    spentMinor = spent,
    priorMinor = prior,
    limitMinor = limit,
    shareOfExpense = 0.0,
)

internal fun summary(
    categories: List<CategorySpend> = listOf(
        spend("dining", spent = 1_800_00, prior = 1_200_00, limit = 1_500_00),
        spend("fuel", spent = 400_00, prior = 420_00),
    ),
    income: Long = 12_500_00,
    priorIncome: Long? = 12_500_00,
    priorExpense: Long? = 6_900_00,
    uncategorized: Long = 340_00,
    uncategorizedCount: Int = 3,
    period: SummaryPeriod = SummaryPeriod.CURRENT_MONTH,
) = InsightsSummary(
    period = period,
    windowStart = period.dateRange(TODAY)?.first,
    windowEnd = period.dateRange(TODAY)?.second,
    incomeMinor = income,
    expenseMinor = categories.sumOf { it.spentMinor } + uncategorized,
    priorIncomeMinor = priorIncome,
    priorExpenseMinor = priorExpense,
    categories = categories,
    uncategorizedMinor = uncategorized,
    uncategorizedCount = uncategorizedCount,
)

/** Matches TestClock: 2026-06-17. */
internal val TODAY = LocalDate(2026, 6, 17)

internal fun raw(
    categoryId: String? = "dining",
    headline: String = "Dining is over its limit",
    detail: String = "Three weekend orders pushed it past 1,500.",
    suggestedLimitMinor: Long? = null,
) = RawNarrativeInsight(categoryId, headline, detail, suggestedLimitMinor)
