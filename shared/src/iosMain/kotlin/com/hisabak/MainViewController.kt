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

/** Xcode entry point: the shared app shell with the iOS platform slots. Tier-1 actuals are
 *  live (NSDateFormatter dates, LocalAuthentication app lock, UNUserNotificationCenter);
 *  the backup seams stay inert until PR B4. */
fun MainViewController(): UIViewController {
    startIosApp()
    return ComposeUIViewController {
        val dateFormatter = remember { IosLocalizedDateFormatter() }
        CompositionLocalProvider(LocalDateFormatter provides dateFormatter) {
            HisabakRoot(
                PlatformSlots(
                    onboarding = { IosOnboardingRoute() },
                    restore = { IosRestoreRoute() },
                    smsInbox = { modifier -> IosSmsInboxRoute(modifier = modifier) },
                    settings = { onOpenBackup, modifier ->
                        IosSettingsRoute(onOpenBackup = onOpenBackup, modifier = modifier)
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
