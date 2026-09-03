package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.core.domain.AppPreferences
import com.hisabak.feature.insights.domain.InsightsSummary
import kotlinx.coroutines.flow.first

/**
 * [AiInsights] backed by the service's `/v1/insights`.
 *
 * **Strictly opt-in, separately from parsing:** `insightsEnabled` is its own preference because
 * parsing sends one bank SMS and this sends a picture of the user's finances — one consent must not
 * imply the other. Re-checked per call, so turning it off takes effect at once.
 */
class RemoteAiInsights(
    private val client: RemoteInsightsClient,
    private val preferences: AppPreferences,
    private val currency: Currency,
) : AiInsights {

    override suspend fun isEnabled(): Boolean =
        client.isConfigured && preferences.insightsEnabled.first()

    override suspend fun narrate(summary: InsightsSummary, language: String): List<RawNarrativeInsight>? {
        if (!isEnabled()) return null
        val response = client.narrate(summary.toRequestDto(language, currency)) ?: return null
        return response.items.map { it.toDomain() }
    }
}
