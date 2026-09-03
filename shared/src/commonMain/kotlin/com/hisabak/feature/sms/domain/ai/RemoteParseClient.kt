package com.hisabak.feature.sms.domain.ai

/**
 * Transport seam for the parse service. Mirrors the `BackupRemote` pattern: the contract and all
 * decision-making live in common code, each platform supplies only the HTTP call
 * (`HttpURLConnection` on Android, `NSURLSession` on iOS).
 *
 * Implementations never throw — an unreachable service, a timeout, or a non-200 is `null`, which
 * the caller treats as "no suggestion" and falls back to the regex templates. A parse failure must
 * never cost the user a capture.
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

/** Where the parse service lives. Absent url or token disables the remote parser entirely. */
data class ParseServiceConfig(
    val baseUrl: String,
    val token: String,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}
