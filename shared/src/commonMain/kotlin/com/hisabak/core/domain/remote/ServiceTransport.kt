package com.hisabak.core.domain.remote

/**
 * The one HTTP seam to Hisabak's service (`server/`): a JSON POST to a path, a JSON body back.
 *
 * Every endpoint client — parsing, insights — is common code over this, so each platform supplies
 * exactly one transport (`HttpURLConnection` on Android, `NSURLSession` on iOS) and the wire
 * contract lives in commonMain where the two platforms cannot drift apart.
 *
 * Implementations never throw. An unreachable service, a timeout, or a non-200 is `null`; the
 * caller degrades — to the regex templates, to the deterministic review — and a service outage
 * must never cost the user a capture or an error screen.
 */
interface ServiceTransport {
    /** True when the build has a base URL and token configured — not that the service is reachable. */
    val isConfigured: Boolean

    suspend fun postJson(path: String, body: String, timeoutMs: Int): String?
}

/** Where the service lives. A blank url or token disables every remote feature. */
data class ServiceConfig(
    val baseUrl: String,
    val token: String,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() && token.isNotBlank()
}
