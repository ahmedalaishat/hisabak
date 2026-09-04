package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.InsightCategory

/**
 * One item of the AI narrative as the model returned it — unvalidated. [categoryId] is whatever
 * string came back; [sanitizeNarrative] decides whether it names a real category.
 */
data class RawNarrativeInsight(
    val categoryId: String?,
    val headline: String,
    val detail: String,
    val suggestedLimitMinor: Long?,
)

/**
 * One item of the narrative after [sanitizeNarrative]: the category is resolved (or absent, for a
 * period-wide item), the text is bounded, and a suggestion — if any — is a plausible monthly cap
 * for that category. This is what the screen renders and the cache stores.
 */
data class NarrativeInsight(
    val category: InsightCategory?,
    val headline: String,
    val detail: String,
    val suggestedLimitMinor: Long? = null,
) {
    /** Stable across recomputation, so list keys survive a refresh. */
    val id: String get() = "ai:${category?.id?.value ?: "-"}:${headline.hashCode()}"
}
