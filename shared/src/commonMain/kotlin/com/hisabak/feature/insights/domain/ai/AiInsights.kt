package com.hisabak.feature.insights.domain.ai

import com.hisabak.feature.insights.domain.InsightsSummary

/**
 * The narrative port (layer 2 of the insights feature). Given the aggregate summary, returns the
 * model's items — or `null` when the service is off or unreachable.
 *
 * Only aggregates cross this boundary: [InsightsSummary] has no field for a transaction, a note, a
 * brand, or a message, so none can leave. Consent is **per request**: nothing calls [narrate]
 * except the user's own tap on the insights screen, so there is no stored opt-in to check here.
 * The caller sanitizes the reply; this port applies no rules of its own.
 */
interface AiInsights {
    /** True when the build has a service configured — the gate for showing the ask at all. */
    fun isAvailable(): Boolean

    /** [language] is the app's UI language tag ("en" | "ar") — the text comes back in it. */
    suspend fun narrate(summary: InsightsSummary, language: String): List<RawNarrativeInsight>?
}
