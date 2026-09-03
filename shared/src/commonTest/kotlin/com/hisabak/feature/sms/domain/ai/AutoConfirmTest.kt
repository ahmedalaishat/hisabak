package com.hisabak.feature.sms.domain.ai

import com.hisabak.feature.sms.domain.capture.CaptureSource
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoConfirmTest {

    private fun gate(
        enabled: Boolean = true,
        source: CaptureSource = CaptureSource.SMS_BROADCAST,
        verifiedPattern: String? = "AED {amount} at {brand}",
        brandIsKnown: Boolean = true,
        amountMinor: Long = 5_000L,
    ) = shouldAutoConfirm(enabled, source, verifiedPattern, brandIsKnown, amountMinor)

    @Test
    fun `every condition met`() {
        assertTrue(gate())
    }

    @Test
    fun `off unless the user turned it on`() {
        assertFalse(gate(enabled = false))
    }

    @Test
    fun `a pasted message is always confirmed by hand`() {
        // The user is on the screen with the suggestion in front of them.
        assertFalse(gate(source = CaptureSource.MANUAL_PASTE))
    }

    @Test
    fun `unverified output is never auto-confirmed`() {
        // No pattern means the amount or merchant could not be found in the message at all.
        assertFalse(gate(verifiedPattern = null))
    }

    @Test
    fun `it will not silently invent a merchant`() {
        assertFalse(gate(brandIsKnown = false))
    }

    @Test
    fun `large amounts are confirmed by hand`() {
        assertTrue(gate(amountMinor = AUTO_CONFIRM_CEILING_MINOR))
        assertFalse(gate(amountMinor = AUTO_CONFIRM_CEILING_MINOR + 1))
    }

    @Test
    fun `a non-positive amount never passes`() {
        assertFalse(gate(amountMinor = 0))
        assertFalse(gate(amountMinor = -1))
    }
}
