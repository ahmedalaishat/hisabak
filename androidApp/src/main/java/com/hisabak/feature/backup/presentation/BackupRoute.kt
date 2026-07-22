package com.hisabak.feature.backup.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupRoute(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The Drive consent screen returns here; hand the result back to the ViewModel.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.data) }

    BackupScreen(
        state = state,
        onSetEnabled = viewModel::setEnabled,
        onSetEncryptionEnabled = viewModel::setEncryptionEnabled,
        onSetPassphrase = viewModel::setPassphrase,
        onSetPeriod = viewModel::setAutoBackupPeriod,
        onConnectAccount = {
            viewModel.connect { intentSender ->
                consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        },
        onBackupNow = viewModel::backupNow,
        onClearError = viewModel::clearError,
        onDismissSync = viewModel::dismissSync,
        modifier = modifier,
    )
}
