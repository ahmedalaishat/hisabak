package com.hisabak.feature.insights.domain

import com.hisabak.feature.category.domain.CategoryId
import kotlin.math.abs

enum class InsightType {
    OverLimit,
    NearLimit,
    SpendUp,
    SpendDown,
    NewSpend,
    LargestCategory,
    SavingsRate,
    Uncategorized,
}

/** Ordered so that `ordinal` sorts Warning first when descending. */
enum class Severity { Info, Notice, Warning }

/** The category an insight is about, carried whole so the screen needs no second lookup. */
data class InsightCategory(val id: CategoryId, val name: String, val color: String, val icon: String)

/**
 * One finding. Carries figures, never copy: the screen renders text from [type] and the fields,
 * which keeps these rules testable without resources and gives Arabic the same treatment as
 * English. What [amountMinor] *means* depends on [type] — the overage for [InsightType.OverLimit],
 * the remaining room for [InsightType.NearLimit], the delta for a change, the total otherwise.
 */
data class Insight(
    val type: InsightType,
    val severity: Severity,
    val category: InsightCategory? = null,
    val amountMinor: Long? = null,
    val deltaPct: Double? = null,
    val share: Double? = null,
    val count: Int? = null,
) {
    /** Stable across recomputation, so list keys survive a refresh. */
    val id: String get() = "$type:${category?.id?.value ?: "-"}"
}

/** A category counts as near its limit from this fraction of it. */
const val NEAR_LIMIT_RATIO = 0.8

/** A period-over-period move smaller than this is not worth a card. */
const val CHANGE_THRESHOLD_PCT = 25.0

/**
 * A category below this share of total expense in *both* periods is immaterial: a 300% rise on
 * a 2-dirham base is noise, and a review that reports noise stops being read.
 */
const val MATERIAL_SHARE = 0.05

/**
 * The deterministic review. Pure, and total — every rule degrades to "absent" rather than
 * throwing when its inputs are missing (no prior period, no income, no limits), so a sparse
 * ledger yields a short review, not an error.
 */
fun deriveInsights(summary: InsightsSummary): List<Insight> {
    val insights = mutableListOf<Insight>()
    insights += limitInsights(summary)
    insights += changeInsights(summary)
    largestCategory(summary)?.let { insights += it }
    savingsRate(summary)?.let { insights += it }
    uncategorized(summary)?.let { insights += it }
    return insights.sortedWith(
        compareByDescending<Insight> { it.severity.ordinal }
            .thenByDescending { abs(it.amountMinor ?: 0L) },
    )
}

private fun InsightsSummary.categoryRef(spend: CategorySpend) =
    InsightCategory(spend.id, spend.name, spend.color, spend.icon)

private fun limitInsights(s: InsightsSummary): List<Insight> = s.categories.mapNotNull { c ->
    val limit = c.limitMinor?.takeIf { it > 0 } ?: return@mapNotNull null
    val used = c.spentMinor.toDouble() / limit
    when {
        c.spentMinor > limit -> Insight(
            type = InsightType.OverLimit,
            severity = Severity.Warning,
            category = s.categoryRef(c),
            amountMinor = c.spentMinor - limit,
            share = used,
        )
        used >= NEAR_LIMIT_RATIO -> Insight(
            type = InsightType.NearLimit,
            severity = Severity.Notice,
            category = s.categoryRef(c),
            amountMinor = limit - c.spentMinor,
            share = used,
        )
        else -> null
    }
}

private fun changeInsights(s: InsightsSummary): List<Insight> {
    if (s.expenseMinor <= 0) return emptyList()
    val floor = s.expenseMinor * MATERIAL_SHARE
    return s.categories.mapNotNull { c ->
        val prior = c.priorMinor ?: return@mapNotNull null
        if (maxOf(c.spentMinor, prior) < floor) return@mapNotNull null
        when {
            prior == 0L && c.spentMinor > 0 -> Insight(
                type = InsightType.NewSpend,
                severity = Severity.Notice,
                category = s.categoryRef(c),
                amountMinor = c.spentMinor,
            )
            prior > 0L -> {
                val pct = (c.spentMinor - prior) * 100.0 / prior
                when {
                    pct >= CHANGE_THRESHOLD_PCT -> Insight(
                        type = InsightType.SpendUp,
                        severity = Severity.Notice,
                        category = s.categoryRef(c),
                        amountMinor = c.spentMinor - prior,
                        deltaPct = pct,
                    )
                    pct <= -CHANGE_THRESHOLD_PCT -> Insight(
                        type = InsightType.SpendDown,
                        severity = Severity.Info,
                        category = s.categoryRef(c),
                        amountMinor = c.spentMinor - prior,
                        deltaPct = pct,
                    )
                    else -> null
                }
            }
            else -> null
        }
    }
}

private fun largestCategory(s: InsightsSummary): Insight? {
    if (s.expenseMinor <= 0) return null
    val top = s.categories.filter { it.spentMinor > 0 }.maxByOrNull { it.spentMinor } ?: return null
    return Insight(
        type = InsightType.LargestCategory,
        severity = Severity.Info,
        category = s.categoryRef(top),
        amountMinor = top.spentMinor,
        share = top.shareOfExpense,
    )
}

private fun savingsRate(s: InsightsSummary): Insight? {
    if (s.incomeMinor <= 0) return null
    val saved = s.incomeMinor - s.expenseMinor
    val rate = saved.toDouble() / s.incomeMinor
    val priorIncome = s.priorIncomeMinor
    val priorExpense = s.priorExpenseMinor
    val deltaPoints = if (priorIncome != null && priorIncome > 0 && priorExpense != null) {
        (rate - (priorIncome - priorExpense).toDouble() / priorIncome) * 100.0
    } else {
        null
    }
    return Insight(
        type = InsightType.SavingsRate,
        // Spending more than you earned is the one figure here that is unambiguously bad.
        severity = if (rate < 0) Severity.Warning else Severity.Info,
        amountMinor = saved,
        share = rate,
        deltaPct = deltaPoints,
    )
}

private fun uncategorized(s: InsightsSummary): Insight? {
    if (s.uncategorizedCount <= 0) return null
    // Notice, not Info: uncategorized spend distorts every other figure in the review.
    return Insight(
        type = InsightType.Uncategorized,
        severity = Severity.Notice,
        amountMinor = s.uncategorizedMinor,
        count = s.uncategorizedCount,
    )
}
