package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.InsightsSummary

/**
 * The identity of a narrative: a digest of **exactly** what was sent.
 *
 * Every figure the request carries is in it — the period's concrete date window (not its name, so
 * "this month" and "this year" stay distinct when their figures coincide and a custom range later
 * needs no new rule), the language, both totals and their priors, the uncategorized total and
 * count, and every category's id, name, spend, prior, and limit. Any change to any of them — one
 * new transaction — is a different input, and a different input asks again rather than showing an
 * answer to a question that is no longer the one on screen. A rounded key was tried and rejected:
 * it kept yesterday's explanation on screen after today's spending.
 *
 * The digest is the cache row's primary key, so answers for different inputs never overwrite each
 * other, and figures that change and change back find their earlier answer without a send.
 */
fun narrativeKey(summary: InsightsSummary, language: String): String {
    val canonical = buildString {
        append(summary.windowStart?.toString() ?: "all").append('|')
        append(summary.windowEnd?.toString() ?: "all").append('|')
        append(language).append('|')
        append(summary.incomeMinor).append('|').append(summary.priorIncomeMinor).append('|')
        append(summary.expenseMinor).append('|').append(summary.priorExpenseMinor).append('|')
        append(summary.uncategorizedMinor).append('|').append(summary.uncategorizedCount)
        for (c in summary.categories) {
            append('\n').append(c.id.value).append('|').append(c.name).append('|')
            append(c.spentMinor).append('|').append(c.priorMinor).append('|').append(c.limitMinor)
        }
    }
    return fnv1a64(canonical)
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
