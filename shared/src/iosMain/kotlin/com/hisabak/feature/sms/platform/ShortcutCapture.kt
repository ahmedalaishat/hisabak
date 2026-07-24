package com.hisabak.feature.sms.platform

import com.hisabak.core.common.DomainResult
import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.sms.domain.capture.CaptureTransactionUseCase
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.shortcut_capture_failure
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
 * capture isn't tied to the intent's lifetime; [completion] receives success plus the localized
 * outcome message, on the main thread.
 *
 * An in-app intent runs in the app's own process (launched headlessly if needed), so
 * `iOSApp.init` → `startIosApp` has already started Koin before `perform()` calls this.
 */
fun captureFromShortcut(text: String, completion: (Boolean, String) -> Unit) {
    val koin = KoinPlatform.getKoin()
    val capture = koin.get<CaptureTransactionUseCase>()
    val scope = koin.get<CoroutineScope>(APPLICATION_SCOPE)
    scope.launch {
        val saved = capture(text, CaptureSource.SHORTCUT) is DomainResult.Success
        val message =
            getString(if (saved) Res.string.shortcut_capture_success else Res.string.shortcut_capture_failure)
        withContext(Dispatchers.Main) { completion(saved, message) }
    }
}
