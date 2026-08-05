package com.hisabak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.hisabak.core.data.preferences.AppLocale
import com.hisabak.feature.backup.presentation.BackupRoute
import com.hisabak.feature.brand.presentation.BrandEditBus
import com.hisabak.feature.dashboard.presentation.CategoryFocusBus
import com.hisabak.feature.notification.platform.SystemNotifier
import com.hisabak.feature.onboarding.presentation.OnboardingRoute
import com.hisabak.feature.restore.presentation.RestoreRoute
import com.hisabak.feature.settings.presentation.SettingsRoute
import com.hisabak.feature.sms.presentation.inbox.SmsInboxRoute
import com.hisabak.security.AppLockGate
import com.hisabak.ui.format.AndroidLocalizedDateFormatter
import com.hisabak.ui.format.LocalDateFormatter
import org.koin.android.ext.android.inject

// FragmentActivity (not plain ComponentActivity) is required by androidx.biometric's BiometricPrompt;
// it still extends ComponentActivity (so Navigation 3 keeps its dispatcher owner) and pulls in
// androidx.fragment, not appcompat — preserving the app's no-appcompat constraint.
class MainActivity : FragmentActivity() {

    private val categoryFocusBus: CategoryFocusBus by inject()
    private val brandEditBus: BrandEditBus by inject()
    private val inboxOpenBus: com.hisabak.feature.sms.presentation.InboxOpenBus by inject()

    // Apply the saved app language before any resources are resolved.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleFocusIntent(intent)
        setContent {
            // Shared screens format dates/sizes through this port; Android backs it with
            // java.time + DateUtils so output matches the pre-KMP behavior exactly.
            val dateFormatter = remember { AndroidLocalizedDateFormatter(this) }
            CompositionLocalProvider(LocalDateFormatter provides dateFormatter) {
                HisabakRoot(remember { androidPlatformSlots() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleFocusIntent(intent)
    }

    /** A notification tap carries either a category to focus (dashboard) or an uncategorized brand
     *  to open in the editor; publish whichever is present so the nav layer can react. */
    private fun handleFocusIntent(intent: Intent?) {
        intent?.getStringExtra(SystemNotifier.EXTRA_CATEGORY_ID)?.let(categoryFocusBus::request)
        intent?.getStringExtra(SystemNotifier.EXTRA_BRAND_ID)?.let(brandEditBus::request)
        if (intent?.getBooleanExtra(SystemNotifier.EXTRA_OPEN_INBOX, false) == true) inboxOpenBus.request()
    }

    private fun androidPlatformSlots() = PlatformSlots(
        onboarding = { OnboardingRoute() },
        restore = { RestoreRoute() },
        smsInbox = { onCreateTemplate, onReviewTransaction, modifier ->
            SmsInboxRoute(
                onCreateTemplate = onCreateTemplate,
                onReviewTransaction = onReviewTransaction,
                modifier = modifier,
            )
        },
        settings = { onOpenBackup, onOpenSmsTemplates, modifier ->
            SettingsRoute(
                onOpenBackup = onOpenBackup,
                onOpenSmsTemplates = onOpenSmsTemplates,
                modifier = modifier,
            )
        },
        backup = { modifier -> BackupRoute(modifier = modifier) },
        appLockGate = { content -> AppLockGate(content = content) },
        notificationPermissionEffect = {
            // Ask for notification permission once on first launch (Android 13+).
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) {}
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        },
        systemBarStyler = { darkTheme ->
            // Keep the system-bar icons legible against the app's resolved theme rather than the
            // system uiMode that enableEdgeToEdge() keys off — otherwise picking Light while the
            // device is in Dark (or switching theme in-app) leaves light icons on a light bar.
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        },
    )
}
