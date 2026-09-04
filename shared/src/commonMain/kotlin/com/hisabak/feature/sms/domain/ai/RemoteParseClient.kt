package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.domain.remote.ServiceTransport
import kotlinx.serialization.json.Json

/**
 * Endpoint client for `/v1/parse`. The contract and all decision-making live here in common code;
 * the platform supplies only the HTTP call through [ServiceTransport].
 *
 * Never throws — an unreachable service, a timeout, or a non-200 is `null`, which the caller treats
 * as "no suggestion" and falls back to the regex templates. A parse failure must never cost the
 * user a capture.
 */
interface RemoteParseClient {
    /** True when the service is configured (base URL + token present) — not that it is reachable. */
    val isConfigured: Boolean

    suspend fun parse(request: RemoteParseRequest): AiParsedSms?
}

/**
 * [todayIso] is only meaningful for [freeText]; a bank alert states its own date and the model is
 * told never to invent one.
 */
data class RemoteParseRequest(
    val text: String,
    val knownBrands: List<String>,
    val todayIso: String?,
    val freeText: Boolean,
)

class ServiceRemoteParseClient(
    private val transport: ServiceTransport,
) : RemoteParseClient {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override val isConfigured: Boolean get() = transport.isConfigured

    override suspend fun parse(request: RemoteParseRequest): AiParsedSms? {
        if (!isConfigured) return null
        val payload = json.encodeToString(
            ParseRequestDto.serializer(),
            ParseRequestDto(
                text = request.text,
                knownBrands = request.knownBrands,
                todayIso = request.todayIso,
                freeText = request.freeText,
            ),
        )
        // Short: this runs while the user watches a spinner on the inbox row.
        val body = transport.postJson("/v1/parse", payload, timeoutMs = TIMEOUT_MS) ?: return null
        return runCatching { json.decodeFromString(ParseResponseDto.serializer(), body).toDomain() }.getOrNull()
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
    }
}
