package com.hisabak

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** Xcode entry point: the shared app shell with the iOS platform slots. The default slot values
 *  cover the seams iOS doesn't fill yet (app lock pass-through, no notification-permission ask,
 *  no system-bar styling), and `LocalDateFormatter` falls back to `BasicLocalizedDateFormatter`.
 *  TODO(Phase-B): real slots arrive tier by tier (B3 local UX, B4 backup). */
fun MainViewController(): UIViewController {
    startIosApp()
    return ComposeUIViewController {
        HisabakRoot(
            PlatformSlots(
                onboarding = { IosOnboardingRoute() },
                restore = { IosRestoreRoute() },
                smsInbox = { modifier -> IosSmsInboxRoute(modifier = modifier) },
                settings = { onOpenBackup, modifier ->
                    IosSettingsRoute(onOpenBackup = onOpenBackup, modifier = modifier)
                },
                backup = { modifier -> IosBackupRoute(modifier = modifier) },
            ),
        )
    }
}
