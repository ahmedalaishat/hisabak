package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.InsightsSummary
import com.hisabak.feature.insights.domain.deriveInsights

/**
 * The identity of a narrative: a digest of what was explained.
 *
 * Keying on the summary itself would regenerate — and pay for — a narrative per transaction. This
 * digest changes only when the review would read differently: a deterministic finding appears or
 * disappears, or a total moves in its second significant digit (roughly a 5–10% step). It also
 * covers the language the text was generated in, and the period's **concrete date window** rather
 * than its name — so "this month" and "this year" stay distinct even when every transaction is in
 * the current month and their figures coincide, and a custom range later needs no new rule.
 *
 * The digest is the cache row's primary key, so answers for different windows never overwrite
 * each other and a window whose figures change and change back finds its earlier answer.
 */
fun narrativeKey(summary: InsightsSummary, language: String): String {
    val findings = deriveInsights(summary).map { it.id }.sorted().joinToString(",")
    val canonical = listOf(
        summary.windowStart?.toString() ?: "all",
        summary.windowEnd?.toString() ?: "all",
        language,
        findings,
        roundToTwoSignificant(summary.incomeMinor),
        roundToTwoSignificant(summary.expenseMinor),
        roundToTwoSignificant(summary.uncategorizedMinor),
    ).joinToString("|")
    return fnv1a64(canonical)
}

internal fun roundToTwoSignificant(value: Long): Long {
    if (value < 100) return value
    var magnitude = 1L
    var v = value
    while (v >= 100) { v /= 10; magnitude *= 10 }
    return v * magnitude
}

/** FNV-1a, 64-bit, as 16 hex chars — a stable, dependency-free digest for a cache key. */
internal fun fnv1a64(text: String): String {
    var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    for (byte in text.encodeToByteArray()) {
        hash = hash xor (byte.toLong() and 0xff)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16).padStart(16, '0')
}
