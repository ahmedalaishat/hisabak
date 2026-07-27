package com.hisabak.core.data.backup

/**
 * Query parameters for Google's OAuth 2.0 authorization endpoint (installed-app + PKCE flow),
 * in the order they're sent. The caller percent-encodes the values and joins them.
 *
 * Pure, and deliberately out of the platform authorizer, because a wrong parameter set here fails
 * *silently*: authorization still completes and Drive works until the access token expires, then
 * there's no refresh token to renew with. That's hours away from the change that caused it, so it
 * needs a test — and the iOS authorizer itself is platform glue the JVM suite can't reach.
 */
fun googleAuthorizationParams(
    clientId: String,
    redirectUri: String,
    scope: String,
    codeChallenge: String,
): List<Pair<String, String>> = listOf(
    "client_id" to clientId,
    "redirect_uri" to redirectUri,
    "response_type" to "code",
    "scope" to scope,
    // Google mints a refresh token only for `access_type=offline`, and on a repeat authorization
    // of an already-granted account only re-mints one when consent is forced. Both are required
    // for unattended auto-backup to survive the first token expiry.
    "access_type" to "offline",
    "prompt" to "consent",
    "code_challenge" to codeChallenge,
    "code_challenge_method" to "S256",
)
