package com.hisabak.feature.sms.domain.ai

import com.hisabak.testutil.FakeAiSmsParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PreferredAiSmsParserTest {

    private val onDevice = FakeAiSmsParser()
    private val remote = FakeAiSmsParser()
    private val parser = PreferredAiSmsParser(onDevice, remote)

    private val body = "Purchase of AED 12.50 at Noon"
    private val fromRemote = AiParsedSms("Noon", 12_50, "AED", null)
    private val fromDevice = AiParsedSms("Noon", 99_99, "AED", null)

    @Test
    fun `the service wins when the user has enabled it`() = runTest {
        onDevice.availability = AiParserAvailability.Ready
        onDevice.result = fromDevice
        remote.availability = AiParserAvailability.Ready
        remote.result = fromRemote

        // Enabling the online model is a preference, not just permission — a switch that says it
        // uses a better model has to actually use it.
        assertEquals(fromRemote, parser.parse(body, emptyList()))
        assertTrue(onDevice.parsedBodies.isEmpty())
    }

    @Test
    fun `without the opt-in nothing is transmitted`() = runTest {
        onDevice.availability = AiParserAvailability.Ready
        onDevice.result = fromDevice
        remote.availability = AiParserAvailability.Unavailable

        assertEquals(fromDevice, parser.parse(body, emptyList()))
        assertTrue(remote.parsedBodies.isEmpty())
    }

    @Test
    fun `an unreachable service falls back to the device`() = runTest {
        remote.availability = AiParserAvailability.Ready
        remote.result = null
        onDevice.availability = AiParserAvailability.Ready
        onDevice.result = fromDevice

        // Offline, an outage, or a spent daily budget — capture still works.
        assertEquals(fromDevice, parser.parse(body, emptyList()))
    }

    @Test
    fun `a device with no model relies on the service`() = runTest {
        onDevice.availability = AiParserAvailability.Unavailable
        remote.availability = AiParserAvailability.Ready
        remote.result = fromRemote

        assertEquals(fromRemote, parser.parse(body, emptyList()))
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
        assertNull(parser.parseFreeText("lunch 45", emptyList(), "2026-09-03, Thursday"))
    }

    @Test
    fun `free text follows the same order`() = runTest {
        onDevice.availability = AiParserAvailability.Ready
        onDevice.freeTextResult = fromDevice
        remote.availability = AiParserAvailability.Ready
        remote.freeTextResult = fromRemote

        assertEquals(fromRemote, parser.parseFreeText("lunch 12.50", emptyList(), "2026-09-03, Thursday"))
    }
}
