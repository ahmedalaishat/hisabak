package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.Currency
import com.hisabak.feature.insights.domain.InsightsSummary

/**
 * [AiInsights] backed by the service's `/v1/insights`.
 *
 * Deliberately no preference: a stored switch would send a picture of the user's finances on every
 * material change without a further decision. Here the send *is* the tap — "Explain with AI" on the
 * insights screen, next to "See what's shared" — so every transmission is one the user just asked
 * for, and there is nothing to revoke.
 */
class RemoteAiInsights(
    private val client: RemoteInsightsClient,
    private val currency: Currency,
) : AiInsights {

    override fun isAvailable(): Boolean = client.isConfigured

    override suspend fun narrate(summary: InsightsSummary, language: String): List<RawNarrativeInsight>? {
        if (!isAvailable()) return null
        val response = client.narrate(summary.toRequestDto(language, currency)) ?: return null
        return response.items.map { it.toDomain() }
    }
}
