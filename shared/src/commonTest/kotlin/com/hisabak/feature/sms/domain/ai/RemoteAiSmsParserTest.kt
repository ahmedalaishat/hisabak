package com.hisabak.feature.sms.domain.ai

import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeRemoteParseClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class RemoteAiSmsParserTest {

    private val client = FakeRemoteParseClient(result = AiParsedSms("Noon", 12_50, "AED", null))
    private val prefs = FakeAppPreferences()
    private val parser = RemoteAiSmsParser(client, prefs)

    @Test
    fun `nothing is sent until the user opts in`() = runTest {
        assertEquals(AiParserAvailability.Unavailable, parser.availability())

        assertNull(parser.parse("Purchase of AED 12.50 at Noon", emptyList()))

        // The consent gate is the whole reason this parser is separate from the on-device one.
        assertTrue(client.requests.isEmpty())
    }

    @Test
    fun `an opted-in user reaches the service`() = runTest {
        prefs.setRemoteParseEnabled(true)

        val result = parser.parse("Purchase of AED 12.50 at Noon", listOf("Noon", "Talabat"))

        assertEquals(AiParsedSms("Noon", 12_50, "AED", null), result)
        assertEquals(listOf("Noon", "Talabat"), client.requests.single().knownBrands)
        assertEquals(false, client.requests.single().freeText)
    }

    @Test
    fun `revoking consent stops sending immediately`() = runTest {
        prefs.setRemoteParseEnabled(true)
        parser.parse("first", emptyList())

        prefs.setRemoteParseEnabled(false)
        assertNull(parser.parse("second", emptyList()))

        assertEquals(1, client.requests.size)
    }

    @Test
    fun `an unconfigured service is unavailable even when opted in`() = runTest {
        prefs.setRemoteParseEnabled(true)
        client.isConfigured = false

        assertEquals(AiParserAvailability.Unavailable, parser.availability())
        assertNull(parser.parse("Purchase of AED 12.50 at Noon", emptyList()))
    }

    @Test
    fun `free text carries today's date to the service`() = runTest {
        prefs.setRemoteParseEnabled(true)

        parser.parseFreeText("lunch 45 yesterday", emptyList(), "2026-09-02, Wednesday")

        val request = client.requests.single()
        assertTrue(request.freeText)
        assertEquals("2026-09-02, Wednesday", request.todayIso)
    }
}
