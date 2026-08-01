package com.hisabak.feature.sms.platform

import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.feature.sms.domain.capture.ShortcutOutcome
import com.hisabak.feature.sms.domain.capture.shortcutOutcomeFor
import com.hisabak.feature.sms.presentation.InboxOpenBus
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.shortcut_capture_duplicate
import com.hisabak.shared.resources.shortcut_capture_failure
import com.hisabak.shared.resources.shortcut_capture_review
import com.hisabak.shared.resources.shortcut_capture_success
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform

/**
 * Entry point for the Shortcuts "Capture transaction" App Intent
 * (`CaptureTransactionIntent.swift`) — the iOS counterpart of androidApp's `CaptureActivity`:
 * ingest the text the shortcut passes, report the outcome. Runs on the application scope so the
 * capture isn't tied to the intent's lifetime; [completion] receives the localized outcome
 * message plus a needs-review flag (true when the message landed in the inbox unparsed), so
 * the intent can return a value Shortcuts can branch on. Runs in the app's own process, so
 * Koin is already started by `iOSApp.init` when `perform()` calls this.
 */
fun captureFromShortcut(text: String, completion: (String, Boolean) -> Unit) {
    val koin = KoinPlatform.getKoin()
    val capture = koin.get<CaptureTransactionUseCase>()
    val scope = koin.get<CoroutineScope>(APPLICATION_SCOPE)
    scope.launch {
        val outcome = shortcutOutcomeFor(capture(text, CaptureSource.SHORTCUT))
        val message = getString(
            when (outcome) {
                ShortcutOutcome.Recorded -> Res.string.shortcut_capture_success
                ShortcutOutcome.NeedsReview -> Res.string.shortcut_capture_review
                ShortcutOutcome.Duplicate -> Res.string.shortcut_capture_duplicate
                ShortcutOutcome.Failed -> Res.string.shortcut_capture_failure
            },
        )
        withContext(Dispatchers.Main) { completion(message, outcome == ShortcutOutcome.NeedsReview) }
    }
}

/** Entry point for the "Open SMS inbox" App Intent — lands the app on the SMS tab. */
fun openInboxFromShortcut() {
    KoinPlatform.getKoin().get<InboxOpenBus>().request()
}
