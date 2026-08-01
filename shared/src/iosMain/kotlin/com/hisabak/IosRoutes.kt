package com.hisabak

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.domain.security.AuthAvailability
import com.hisabak.core.platform.security.IosBiometricAuthenticator
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.app_lock_prompt_title
import com.hisabak.shared.resources.sms_ai_parse_failed
import org.jetbrains.compose.resources.stringResource
import com.hisabak.feature.settings.presentation.LANGUAGE_ARABIC
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import com.hisabak.feature.backup.presentation.BackupScreen
import com.hisabak.feature.backup.presentation.BackupViewModel
import com.hisabak.feature.onboarding.presentation.OnboardingScreen
import com.hisabak.feature.onboarding.presentation.OnboardingViewModel
import com.hisabak.feature.restore.presentation.RestoreScreen
import com.hisabak.feature.restore.presentation.RestoreViewModel
import com.hisabak.feature.settings.presentation.LANGUAGE_ENGLISH
import com.hisabak.feature.settings.presentation.SettingsScreen
import com.hisabak.feature.settings.presentation.SettingsViewModel
import com.hisabak.feature.sms.presentation.inbox.SmsInboxEffect
import com.hisabak.feature.sms.presentation.inbox.SmsInboxIntent
import com.hisabak.feature.sms.presentation.inbox.SmsInboxScreen
import com.hisabak.feature.sms.presentation.inbox.SmsInboxViewModel
import com.hisabak.feature.sms.presentation.inbox.formatMoney
import com.hisabak.core.domain.ThemeMode
import org.koin.compose.viewmodel.koinViewModel

// iOS counterparts of androidApp's thin Routes. Android's launcher/IntentSender affordances map
// to their iOS equivalents inline: biometrics via LocalAuthentication (B3), Drive consent via
// ASWebAuthenticationSession inside DriveAuthorizer.authorize() (B4), and language via the iOS
// per-app language setting. SMS auto-capture has no iOS equivalent — manual paste/share only,
// matching the Android Play build (smsAutoCapture=false).

/** No SMS capture exists on iOS, so the final onboarding CTA just completes. */
@Composable
internal fun IosOnboardingRoute(viewModel: OnboardingViewModel = koinViewModel()) {
    OnboardingScreen(onFinish = viewModel::complete)
}

/** Drive consent runs inline in `DriveAuthorizer.authorize()` (ASWebAuthenticationSession),
 *  so the NeedsConsent callback never fires on iOS. */
@Composable
internal fun IosRestoreRoute(viewModel: RestoreViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RestoreScreen(
        state = state,
        onConnect = { viewModel.connect { } },
        onSubmitPassphrase = viewModel::submitPassphrase,
        onSkip = viewModel::skip,
        onFinish = viewModel::finishRestore,
    )
}

@Composable
internal fun IosSmsInboxRoute(
    onCreateTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val aiParseFailedText = stringResource(Res.string.sms_ai_parse_failed)
    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(SmsInboxIntent.ConsumeEffect) },
        onEffect = { effect ->
            when (effect) {
                is SmsInboxEffect.ParseFailed ->
                    snackbarHostState.showSnackbar("Could not parse: ${effect.reason}")
                is SmsInboxEffect.TransactionCreated ->
                    snackbarHostState.showSnackbar("Transaction created: ${formatMoney(effect.amount)}")
                SmsInboxEffect.AiParseFailed ->
                    snackbarHostState.showSnackbar(aiParseFailedText)
            }
        },
    )

    SmsInboxScreen(
        state = state,
        onCreateTemplate = { onCreateTemplate(it.value) },
        snackbarHostState = snackbarHostState,
        autoImportAvailable = false,
        onSearchChange = { viewModel.onIntent(SmsInboxIntent.SearchChanged(it)) },
        onDraftChange = { viewModel.onIntent(SmsInboxIntent.DraftChanged(it)) },
        onIngest = { viewModel.onIntent(SmsInboxIntent.IngestDraft) },
        onDelete = { viewModel.onIntent(SmsInboxIntent.Delete(it)) },
        onEnableAutoImport = {},
        modifier = modifier,
        onSuggestParse = { viewModel.onIntent(SmsInboxIntent.SuggestParse(it)) },
        onConfirmSuggestion = { viewModel.onIntent(SmsInboxIntent.ConfirmSuggestion(it)) },
        onDismissSuggestion = { viewModel.onIntent(SmsInboxIntent.DismissSuggestion(it)) },
    )
}

/** The app lock rides LocalAuthentication. Language follows the iOS per-app language setting
 *  (`CFBundleLocalizations` declares en+ar, so iOS offers the picker natively); tapping the
 *  other language deep-links to the app's page in the Settings app, and iOS relaunches the
 *  app with the new locale — CMP resolves strings off it. */
@Composable
internal fun IosSettingsRoute(
    onOpenBackup: () -> Unit,
    onOpenSmsTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val passphraseReminderVisible by viewModel.passphraseReminderVisible.collectAsStateWithLifecycle(initialValue = false)

    val authenticator = remember { IosBiometricAuthenticator() }
    // Fixed for a device session, same as Android: "none enrolled" still counts as supported.
    val appLockSupported = remember { authenticator.availability() != AuthAvailability.Unavailable }
    val promptTitle = stringResource(Res.string.app_lock_prompt_title)

    val language = if (Locale.current.language == LANGUAGE_ARABIC) LANGUAGE_ARABIC else LANGUAGE_ENGLISH

    SettingsScreen(
        themeMode = themeMode,
        language = language,
        appLockEnabled = appLockEnabled,
        appLockSupported = appLockSupported,
        onThemeChange = viewModel::setThemeMode,
        onLanguageChange = { tag ->
            if (tag != language) {
                UIApplication.sharedApplication.openURL(
                    NSURL(string = UIApplicationOpenSettingsURLString)!!,
                    options = emptyMap<Any?, Any>(),
                    completionHandler = null,
                )
            }
        },
        onAppLockChange = { wantEnabled ->
            // Same rule as Android: turning the lock ON or OFF both require a successful auth,
            // so it can't be flipped by someone holding an unlocked phone; if the device can no
            // longer authenticate, allow the change (consistent with the unlock-time bypass).
            if (authenticator.availability() != AuthAvailability.Available) {
                if (!wantEnabled) viewModel.setAppLockEnabled(false)
            } else {
                authenticator.authenticate(promptTitle) { ok ->
                    if (ok) viewModel.setAppLockEnabled(wantEnabled)
                }
            }
        },
        onOpenBackup = onOpenBackup,
        onOpenSmsTemplates = onOpenSmsTemplates,
        passphraseReminderVisible = passphraseReminderVisible,
        onConfirmRemembered = viewModel::confirmPassphraseRemembered,
        onVerifyPassphrase = viewModel::verifyPassphrase,
        modifier = modifier,
    )
}

@Composable
internal fun IosBackupRoute(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackupScreen(
        state = state,
        onSetEnabled = viewModel::setEnabled,
        onSetEncryptionEnabled = viewModel::setEncryptionEnabled,
        onSetPassphrase = viewModel::setPassphrase,
        onSetPeriod = viewModel::setAutoBackupPeriod,
        onConnectAccount = { viewModel.connect { } },
        onBackupNow = viewModel::backupNow,
        onClearError = viewModel::clearError,
        onDismissSync = viewModel::dismissSync,
        modifier = modifier,
    )
}
