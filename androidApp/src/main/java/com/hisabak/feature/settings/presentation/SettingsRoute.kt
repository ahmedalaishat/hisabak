package com.hisabak.feature.settings.presentation

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.R
import com.hisabak.core.data.preferences.AppLocale
import com.hisabak.core.domain.ThemeMode
import com.hisabak.core.domain.security.AuthAvailability
import com.hisabak.core.platform.security.BiometricAuthenticator
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsRoute(
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val authenticator = koinInject<BiometricAuthenticator>()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val passphraseReminderVisible by viewModel.passphraseReminderVisible.collectAsStateWithLifecycle(initialValue = false)
    // The effective UI language follows the current configuration, so the selection reflects
    // what's actually on screen after a locale switch recreates the activity.
    val language = if (LocalConfiguration.current.locales[0].language == LANGUAGE_ARABIC) {
        LANGUAGE_ARABIC
    } else {
        LANGUAGE_ENGLISH
    }

    // Hardware/credential support is fixed for a device; "none enrolled" still counts as supported
    // because we route the user to set a lock up.
    val appLockSupported = remember { authenticator.availability() != AuthAvailability.Unavailable }
    val promptTitle = stringResource(R.string.app_lock_prompt_title)
    val promptSubtitle = stringResource(R.string.app_lock_prompt_subtitle)

    // After the user returns from system enrollment, enable the lock if a credential now exists.
    val enrollLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (authenticator.availability() == AuthAvailability.Available) {
            viewModel.setAppLockEnabled(true)
        }
    }

    SettingsScreen(
        themeMode = themeMode,
        language = language,
        appLockEnabled = appLockEnabled,
        appLockSupported = appLockSupported,
        onThemeChange = viewModel::setThemeMode,
        onLanguageChange = { tag ->
            if (tag != language) {
                viewModel.onLanguageSelected(tag)
                AppLocale.setLanguageTag(context, tag)
                (context as? Activity)?.recreate()
            }
        },
        onAppLockChange = { wantEnabled ->
            if (!wantEnabled) {
                // Require a successful auth to turn the lock OFF too, so it can't be silently disabled
                // by someone holding the phone (e.g. within the grace window). If the device can no
                // longer authenticate, allow the disable — consistent with the unlock-time bypass.
                if (authenticator.availability() != AuthAvailability.Available) {
                    viewModel.setAppLockEnabled(false)
                } else {
                    (context as? FragmentActivity)?.let { activity ->
                        authenticator.authenticate(activity, promptTitle, promptSubtitle) { ok ->
                            if (ok) viewModel.setAppLockEnabled(false)
                        }
                    }
                }
            } else when (authenticator.availability()) {
                AuthAvailability.Available -> {
                    (context as? FragmentActivity)?.let { activity ->
                        authenticator.authenticate(activity, promptTitle, promptSubtitle) { ok ->
                            if (ok) viewModel.setAppLockEnabled(true)
                        }
                    }
                }
                AuthAvailability.NoneEnrolled -> enrollLauncher.launch(enrollIntent())
                AuthAvailability.Unavailable -> Unit
            }
        },
        onOpenBackup = onOpenBackup,
        passphraseReminderVisible = passphraseReminderVisible,
        onConfirmRemembered = viewModel::confirmPassphraseRemembered,
        onVerifyPassphrase = viewModel::verifyPassphrase,
        modifier = modifier,
    )
}

/** Route to enroll a biometric/device credential — the dedicated screen on API 30+, else Security. */
private fun enrollIntent(): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL,
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
