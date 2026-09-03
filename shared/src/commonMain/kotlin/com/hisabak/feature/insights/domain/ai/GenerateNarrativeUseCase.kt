package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Clock
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.insights.domain.InsightsSummary

sealed interface NarrativeResult {
    /** No service in this build, or the user has not opted in. The screen may offer the opt-in. */
    data object Disabled : NarrativeResult

    /** [fresh] is false when the items came from the cache without a call. */
    data class Ready(val items: List<NarrativeInsight>, val fresh: Boolean) : NarrativeResult

    /** The service could not answer. [stale] is the last narrative for this period, if any. */
    data class Unavailable(val stale: List<NarrativeInsight>?) : NarrativeResult
}

/**
 * Layer 2 of the review: the model's explanation, generated once per material change and cached.
 *
 * The deterministic review is always shown by the caller; this only ever adds to it. Every failure
 * is a [NarrativeResult], never an exception — an outage degrades to the review the user already
 * has. The cache is consulted first and written on every reply, including an empty one: a summary
 * the model had nothing to say about must not be re-sent on every screen open.
 */
class GenerateNarrativeUseCase(
    private val aiInsights: AiInsights,
    private val cache: NarrativeCache,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(summary: InsightsSummary, language: String): NarrativeResult {
        if (!aiInsights.isEnabled()) return NarrativeResult.Disabled
        // Nothing to narrate; a call would only return "you have no spending".
        if (summary.incomeMinor <= 0 && summary.expenseMinor <= 0) return NarrativeResult.Ready(emptyList(), fresh = false)

        val key = narrativeKey(summary, language)
        val cached = cache.get(summary.period)
        if (cached != null && cached.key == key) return NarrativeResult.Ready(cached.items, fresh = false)

        val raw = aiInsights.narrate(summary, language) ?: return NarrativeResult.Unavailable(cached?.items)
        val items = sanitizeNarrative(raw, summary)
        cache.put(CachedNarrative(summary.period, key, items, clock.now()))
        analytics.log(AnalyticsEvent.InsightsNarrativeGenerated(count = items.size, dropped = raw.size - items.size))
        return NarrativeResult.Ready(items, fresh = true)
    }
}
