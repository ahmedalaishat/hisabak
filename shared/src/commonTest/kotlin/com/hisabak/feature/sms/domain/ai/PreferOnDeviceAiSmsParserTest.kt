package com.hisabak.feature.sms.domain.ai

import com.hisabak.testutil.FakeAiSmsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PreferOnDeviceAiSmsParserTest {

    private val onDevice = FakeAiSmsParser()
    private val remote = FakeAiSmsParser()
    private val parser = PreferOnDeviceAiSmsParser(onDevice, remote)

    private val body = "Purchase of AED 12.50 at Noon"
    private val parsed = AiParsedSms("Noon", 12_50, "AED", null)

    @Test
    fun `a working on-device model keeps the message on the phone`() = runTest {
        onDevice.availability = AiParserAvailability.Ready
        onDevice.result = parsed
        remote.availability = AiParserAvailability.Ready

        assertEquals(parsed, parser.parse(body, emptyList()))

        // Privacy, not just preference: with a local answer there is no reason to transmit.
        assertTrue(remote.parsedBodies.isEmpty())
    }

    @Test
    fun `a device with no model falls back to the service`() = runTest {
        onDevice.availability = AiParserAvailability.Unavailable
        remote.availability = AiParserAvailability.Ready
        remote.result = parsed

        assertEquals(parsed, parser.parse(body, emptyList()))
        assertEquals(listOf(body), remote.parsedBodies)
    }

    @Test
    fun `an on-device miss falls through to the better model`() = runTest {
        onDevice.availability = AiParserAvailability.Ready
        onDevice.result = null
        remote.availability = AiParserAvailability.Ready
        remote.result = parsed

        // Nano failing on an unusual format is exactly what the remote service is for.
        assertEquals(parsed, parser.parse(body, emptyList()))
        assertEquals(listOf(body), remote.parsedBodies)
    }

    @Test
    fun `available when either engine is`() = runTest {
        onDevice.availability = AiParserAvailability.Unavailable
        remote.availability = AiParserAvailability.Unavailable
        assertEquals(AiParserAvailability.Unavailable, parser.availability())

        remote.availability = AiParserAvailability.Ready
        assertEquals(AiParserAvailability.Ready, parser.availability())
    }

    @Test
    fun `neither engine available yields nothing`() = runTest {
        onDevice.availability = AiParserAvailability.Unavailable
        remote.availability = AiParserAvailability.Unavailable

        assertNull(parser.parse(body, emptyList()))
        assertNull(parser.parseFreeText("lunch 45", emptyList(), "2026-09-02, Wednesday"))
    }
}
