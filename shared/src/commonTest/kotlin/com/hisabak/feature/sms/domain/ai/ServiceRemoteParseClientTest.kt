package com.hisabak.feature.sms.domain.ai

import com.hisabak.testutil.FakeServiceTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ServiceRemoteParseClientTest {

    private val transport = FakeServiceTransport()
    private val client = ServiceRemoteParseClient(transport)
    private val request = RemoteParseRequest("Purchase of AED 12.50 at Noon", listOf("Noon"), null, false)

    @Test
    fun `posts the snake_case contract to the parse path and maps the reply`() = runTest {
        transport.responses["/v1/parse"] =
            """{"brand":"Noon","brand_text":"Noon","amount_minor":1250,"amount_text":"12.50","currency":"AED","date_iso":null,"model":"x"}"""

        val parsed = client.parse(request)!!

        assertEquals("Noon", parsed.brandName)
        assertEquals(1250, parsed.amountMinor)
        val post = transport.posts.single()
        assertEquals("/v1/parse", post.path)
        assertTrue(post.body.contains("\"known_brands\":[\"Noon\"]"))
        assertTrue(post.body.contains("\"free_text\":false"))
    }

    @Test
    fun `a transport failure or malformed reply is null and never an exception`() = runTest {
        assertNull(client.parse(request))

        transport.responses["/v1/parse"] = "not json"
        assertNull(client.parse(request))
    }

    @Test
    fun `an unconfigured transport is not even asked`() = runTest {
        transport.isConfigured = false

        assertNull(client.parse(request))
        assertTrue(transport.posts.isEmpty())
    }
}
