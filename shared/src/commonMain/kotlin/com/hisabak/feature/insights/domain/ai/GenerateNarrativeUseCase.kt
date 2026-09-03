package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Clock
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.insights.domain.InsightsSummary

sealed interface NarrativeResult {
    /** No service in this build: the screen shows no AI at all. */
    data object Disabled : NarrativeResult

    /** [fresh] is false when the items came from the cache without a call. */
    data class Ready(val items: List<NarrativeInsight>, val fresh: Boolean) : NarrativeResult

    /** The service could not answer. [stale] is the last narrative for this period, if any. */
    data class Unavailable(val stale: List<NarrativeInsight>?) : NarrativeResult
}

/**
 * Layer 2 of the review: the model's explanation, fetched when the user asks and cached per
 * material change.
 *
 * The deterministic review is always shown by the caller; this only ever adds to it. Every failure
 * is a [NarrativeResult], never an exception — an outage degrades to the review the user already
 * has. [cached] lets the screen show an answer already on the phone for exactly these figures
 * **without sending anything**; [invoke] is the send, and only the user's tap calls it. The cache is
 * written on every reply, including an empty one: a summary the model had nothing to say about must
 * not be re-sent on the next tap.
 */
class GenerateNarrativeUseCase(
    private val aiInsights: AiInsights,
    private val cache: NarrativeCache,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    /** The narrative already generated for exactly these figures, or null — never a call. */
    suspend fun cached(summary: InsightsSummary, language: String): List<NarrativeInsight>? {
        val cached = cache.get(summary.period) ?: return null
        return cached.items.takeIf { cached.key == narrativeKey(summary, language) }
    }

    suspend operator fun invoke(summary: InsightsSummary, language: String): NarrativeResult {
        if (!aiInsights.isAvailable()) return NarrativeResult.Disabled
        // Nothing to narrate; a call would only return "you have no spending".
        if (summary.incomeMinor <= 0 && summary.expenseMinor <= 0) return NarrativeResult.Ready(emptyList(), fresh = false)

        val key = narrativeKey(summary, language)
        val cached = cache.get(summary.period)
        if (cached != null && cached.key == key) return NarrativeResult.Ready(cached.items, fresh = false)

        analytics.log(AnalyticsEvent.InsightsNarrativeRequested)
        val raw = aiInsights.narrate(summary, language) ?: return NarrativeResult.Unavailable(cached?.items)
        val items = sanitizeNarrative(raw, summary)
        cache.put(CachedNarrative(summary.period, key, items, clock.now()))
        analytics.log(AnalyticsEvent.InsightsNarrativeGenerated(count = items.size, dropped = raw.size - items.size))
        return NarrativeResult.Ready(items, fresh = true)
    }
}
