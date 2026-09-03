package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.domain.remote.ServiceTransport
import kotlinx.serialization.json.Json

/**
 * Endpoint client for `/v1/insights`, the same shape as `RemoteParseClient`: common code over the
 * platform [ServiceTransport], and `null` for every failure so the review degrades to its
 * deterministic layer rather than showing an error.
 */
interface RemoteInsightsClient {
    val isConfigured: Boolean

    suspend fun narrate(request: InsightsRequestDto): InsightsResponseDto?
}

class ServiceRemoteInsightsClient(
    private val transport: ServiceTransport,
) : RemoteInsightsClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val isConfigured: Boolean get() = transport.isConfigured

    override suspend fun narrate(request: InsightsRequestDto): InsightsResponseDto? {
        if (!isConfigured) return null
        val payload = json.encodeToString(InsightsRequestDto.serializer(), request)
        // Longer than a parse: five items of prose, and nobody is watching a spinner on a row — the
        // deterministic review is already on screen while this runs.
        val body = transport.postJson("/v1/insights", payload, timeoutMs = TIMEOUT_MS) ?: return null
        return runCatching { json.decodeFromString(InsightsResponseDto.serializer(), body) }.getOrNull()
    }

    private companion object {
        const val TIMEOUT_MS = 40_000
    }
}
