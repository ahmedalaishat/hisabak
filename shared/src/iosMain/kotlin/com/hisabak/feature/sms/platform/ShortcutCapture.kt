package com.hisabak.feature.sms.platform

import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.capture.ShortcutOutcome
import com.hisabak.feature.sms.domain.capture.shortcutOutcomeFor
import com.hisabak.feature.sms.presentation.InboxOpenBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform

/**
 * Entry point for the Shortcuts "Capture transaction" App Intent
 * (`CaptureTransactionIntent.swift`). Silent by design: the capture pipeline's notifications
 * ("transaction recorded" / "saved for review", the latter deep-linking to the inbox) are the
 * feedback channel — no intent dialog. [completion] receives only the needs-review flag so the
 * intent can return a value Shortcuts can branch on. Runs in the app's own process, so Koin is
 * already started by `iOSApp.init` when `perform()` calls this.
 */
fun captureFromShortcut(text: String, completion: (Boolean) -> Unit) {
    val koin = KoinPlatform.getKoin()
    val capture = koin.get<CaptureTransactionUseCase>()
    val scope = koin.get<CoroutineScope>(APPLICATION_SCOPE)
    scope.launch {
        val outcome = shortcutOutcomeFor(capture(text, CaptureSource.SHORTCUT))
        withContext(Dispatchers.Main) { completion(outcome == ShortcutOutcome.NeedsReview) }
    }
}

/** Entry point for the "Open SMS inbox" App Intent — lands the app on the SMS tab. */
fun openInboxFromShortcut() {
    KoinPlatform.getKoin().get<InboxOpenBus>().request()
}
