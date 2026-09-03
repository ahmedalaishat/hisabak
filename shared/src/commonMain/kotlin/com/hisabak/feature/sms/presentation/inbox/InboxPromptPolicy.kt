package com.hisabak.feature.sms.presentation.inbox

/**
 * Which offer, if any, the inbox should make. Pure so the rules can be read and tested without a
 * ViewModel around them.
 *
 * **At most one at a time**, and online parsing goes first: it is the capability, auto-confirm is a
 * refinement of it, and two cards stacked above the paste box would read as nagging rather than
 * helping. An offer already taken, permanently declined, or dismissed this launch never appears.
 */
fun choosePrompt(
    remoteConfigured: Boolean,
    remoteEnabled: Boolean,
    aiReady: Boolean,
    autoConfirmEnabled: Boolean,
    suppressed: Set<String>,
    dismissedThisLaunch: Set<InboxPrompt>,
): InboxPrompt? {
    fun offerable(prompt: InboxPrompt) =
        prompt.name !in suppressed && prompt !in dismissedThisLaunch

    // Never offered when the build has no service to reach: that would be a promise it can't keep.
    if (remoteConfigured && !remoteEnabled && offerable(InboxPrompt.OnlineParsing)) {
        return InboxPrompt.OnlineParsing
    }
    // Pointless on a device where nothing can produce a suggestion to confirm.
    if (aiReady && !autoConfirmEnabled && offerable(InboxPrompt.AutoConfirm)) {
        return InboxPrompt.AutoConfirm
    }
    return null
}
