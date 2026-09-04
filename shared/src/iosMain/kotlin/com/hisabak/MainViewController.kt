package com.hisabak

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.hisabak.feature.notification.platform.requestNotificationAuthorization
import com.hisabak.security.IosAppLockGate
import com.hisabak.ui.format.IosLocalizedDateFormatter
import com.hisabak.ui.format.LocalDateFormatter
import platform.UIKit.UIViewController

/** Xcode entry point: the shared app shell with the iOS platform slots. `iOSApp.swift`'s init
 *  calls [startIosApp] (with the Swift CryptoKit bridge) before this — BGTaskScheduler
 *  registration has to precede the end of app launch. */
fun MainViewController(): UIViewController {
    requireIosAppStarted()
    return ComposeUIViewController {
        val dateFormatter = remember { IosLocalizedDateFormatter() }
        CompositionLocalProvider(LocalDateFormatter provides dateFormatter) {
            HisabakRoot(
                PlatformSlots(
                    onboarding = { IosOnboardingRoute() },
                    restore = { IosRestoreRoute() },
                    smsInbox = { onCreateTemplate, onReviewTransaction, modifier ->
                        IosSmsInboxRoute(
                            onCreateTemplate = onCreateTemplate,
                            onReviewTransaction = onReviewTransaction,
                            modifier = modifier,
                        )
                    },
                    settings = { onOpenBackup, onOpenSmsParsing, modifier ->
                        IosSettingsRoute(
                            onOpenBackup = onOpenBackup,
                            onOpenSmsParsing = onOpenSmsParsing,
                            modifier = modifier,
                        )
                    },
                    backup = { modifier -> IosBackupRoute(modifier = modifier) },
                    appLockGate = { content -> IosAppLockGate(content = content) },
                    notificationPermissionEffect = {
                        LaunchedEffect(Unit) { requestNotificationAuthorization() }
                    },
                ),
            )
        }
    }
}
