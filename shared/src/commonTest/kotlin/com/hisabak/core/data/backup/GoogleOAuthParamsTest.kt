package com.hisabak.core.data.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoogleOAuthParamsTest {

    private fun params() = googleAuthorizationParams(
        clientId = "123-abc.apps.googleusercontent.com",
        redirectUri = "com.googleusercontent.apps.123-abc:/oauth2redirect",
        scope = "https://www.googleapis.com/auth/drive.appdata",
        codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    ).toMap()

    @Test
    fun `asks for offline access so a refresh token is issued`() {
        // Without this Google returns an access token only, and unattended auto-backup dies
        // silently at the first expiry.
        assertEquals("offline", params()["access_type"])
    }

    @Test
    fun `forces consent so re-authorization re-mints the refresh token`() {
        // Re-authorizing an already-granted account skips consent by default — and skipped
        // consent means no new refresh token, leaving a stale one in place.
        assertEquals("consent", params()["prompt"])
    }

    @Test
    fun `carries the PKCE challenge and the installed-app code flow`() {
        val p = params()
        assertEquals("code", p["response_type"])
        assertEquals("S256", p["code_challenge_method"])
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", p["code_challenge"])
    }

    @Test
    fun `passes the caller's client, redirect and scope through unchanged`() {
        val p = params()
        assertEquals("123-abc.apps.googleusercontent.com", p["client_id"])
        assertEquals("com.googleusercontent.apps.123-abc:/oauth2redirect", p["redirect_uri"])
        assertEquals("https://www.googleapis.com/auth/drive.appdata", p["scope"])
    }

    @Test
    fun `no parameter is dropped by duplicate names`() {
        val list = googleAuthorizationParams("c", "r", "s", "cc")
        assertEquals(list.size, list.map { it.first }.toSet().size)
        assertTrue(list.none { it.second.isEmpty() })
    }
}
