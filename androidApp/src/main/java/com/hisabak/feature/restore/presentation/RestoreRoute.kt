package com.hisabak.feature.restore.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun RestoreRoute(
    modifier: Modifier = Modifier,
    viewModel: RestoreViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(result.data) }

    RestoreScreen(
        state = state,
        onConnect = {
            viewModel.connect { intentSender ->
                consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
        },
        onSubmitPassphrase = viewModel::submitPassphrase,
        onSkip = viewModel::skip,
        onFinish = viewModel::finishRestore,
        modifier = modifier,
    )
}
