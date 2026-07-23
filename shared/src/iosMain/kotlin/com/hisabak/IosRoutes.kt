package com.hisabak

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
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

// iOS counterparts of androidApp's thin Routes. The platform affordances they wrap on Android
// (permission launchers, Drive consent IntentSenders, biometric enrollment) don't exist here yet,
// so each wires the shared Screen to its ViewModel with those seams inert. TODO(Phase-B): replace
// the inert seams tier by tier (B3 locale/biometrics, B4 Drive consent + backup).

/** No SMS capture exists on iOS, so the final onboarding CTA just completes. */
@Composable
internal fun IosOnboardingRoute(viewModel: OnboardingViewModel = koinViewModel()) {
    OnboardingScreen(onFinish = viewModel::complete)
}

/** The stub DriveAuthorizer reports Unavailable, so connect never asks for consent. */
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
    modifier: Modifier = Modifier,
    viewModel: SmsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = { viewModel.onIntent(SmsInboxIntent.ConsumeEffect) },
        onEffect = { effect ->
            when (effect) {
                is SmsInboxEffect.ParseFailed ->
                    snackbarHostState.showSnackbar("Could not parse: ${effect.reason}")
                is SmsInboxEffect.TransactionCreated ->
                    snackbarHostState.showSnackbar("Transaction created: ${formatMoney(effect.amount)}")
            }
        },
    )

    SmsInboxScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        autoImportAvailable = false,
        onSearchChange = { viewModel.onIntent(SmsInboxIntent.SearchChanged(it)) },
        onDraftChange = { viewModel.onIntent(SmsInboxIntent.DraftChanged(it)) },
        onIngest = { viewModel.onIntent(SmsInboxIntent.IngestDraft) },
        onDelete = { viewModel.onIntent(SmsInboxIntent.Delete(it)) },
        onEnableAutoImport = {},
        modifier = modifier,
    )
}

/** Language switching and the app lock are Phase-B3 seams; both render as fixed/unsupported. */
@Composable
internal fun IosSettingsRoute(
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle(initialValue = false)
    val passphraseReminderVisible by viewModel.passphraseReminderVisible.collectAsStateWithLifecycle(initialValue = false)

    SettingsScreen(
        themeMode = themeMode,
        language = LANGUAGE_ENGLISH,
        appLockEnabled = appLockEnabled,
        appLockSupported = false,
        onThemeChange = viewModel::setThemeMode,
        onLanguageChange = {},
        onAppLockChange = {},
        onOpenBackup = onOpenBackup,
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
