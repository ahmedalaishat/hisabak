package com.hisabak.feature.sms.presentation.inbox

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InboxPromptPolicyTest {

    private fun choose(
        remoteConfigured: Boolean = true,
        remoteEnabled: Boolean = false,
        aiReady: Boolean = true,
        autoConfirmEnabled: Boolean = false,
        suppressed: Set<String> = emptySet(),
        dismissedThisLaunch: Set<InboxPrompt> = emptySet(),
    ) = choosePrompt(
        remoteConfigured, remoteEnabled, aiReady, autoConfirmEnabled, suppressed, dismissedThisLaunch,
    )

    @Test
    fun `online parsing is offered first`() {
        // Both are offerable here; only one card should ever appear, and it is the capability
        // rather than the refinement of it.
        assertEquals(InboxPrompt.OnlineParsing, choose())
    }

    @Test
    fun `auto-confirm follows once online parsing is on`() {
        assertEquals(InboxPrompt.AutoConfirm, choose(remoteEnabled = true))
    }

    @Test
    fun `nothing is offered once both are on`() {
        assertNull(choose(remoteEnabled = true, autoConfirmEnabled = true))
    }

    @Test
    fun `a build with no service never offers online parsing`() {
        // Offering it would be a promise the build cannot keep.
        assertEquals(InboxPrompt.AutoConfirm, choose(remoteConfigured = false))
    }

    @Test
    fun `auto-confirm is not offered where nothing can produce a suggestion`() {
        assertNull(choose(remoteConfigured = false, aiReady = false))
    }

    @Test
    fun `not now hides it for this launch only`() {
        // The offer is gone now, and the next prompt takes its place rather than nothing showing.
        assertEquals(
            InboxPrompt.AutoConfirm,
            choose(dismissedThisLaunch = setOf(InboxPrompt.OnlineParsing)),
        )
    }

    @Test
    fun `don't ask again is permanent`() {
        assertEquals(
            InboxPrompt.AutoConfirm,
            choose(suppressed = setOf(InboxPrompt.OnlineParsing.name)),
        )
        assertNull(
            choose(suppressed = setOf(InboxPrompt.OnlineParsing.name, InboxPrompt.AutoConfirm.name)),
        )
    }
}
