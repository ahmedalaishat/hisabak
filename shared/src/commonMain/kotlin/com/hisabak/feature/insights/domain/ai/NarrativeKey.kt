package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.deriveInsights

/**
 * What "materially changed" means for the narrative cache.
 *
 * Every new transaction changes the summary, so keying the cache on the summary itself would
 * regenerate — and pay for — a narrative per transaction. This key changes only when the review
 * would read differently: a deterministic finding appears or disappears, or a total moves in its
 * second significant digit (roughly a 5–10% step). The language is part of it, since the text is
 * generated in one. Period-scoped: the cache holds one entry per period, and the period is in the
 * cache row, not the key.
 */
fun narrativeKey(summary: InsightsSummary, language: String): String {
    val findings = deriveInsights(summary).map { it.id }.sorted().joinToString(",")
    return listOf(
        language,
        findings,
        roundToTwoSignificant(summary.incomeMinor),
        roundToTwoSignificant(summary.expenseMinor),
        roundToTwoSignificant(summary.uncategorizedMinor),
    ).joinToString("|")
}

internal fun roundToTwoSignificant(value: Long): Long {
    if (value < 100) return value
    var magnitude = 1L
    var v = value
    while (v >= 100) { v /= 10; magnitude *= 10 }
    return v * magnitude
}
