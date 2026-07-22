package com.hisabak.feature.sms.presentation.inbox

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.BuildConfig
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SmsInboxRoute(
    modifier: Modifier = Modifier,
    viewModel: SmsInboxViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Auto-import (RECEIVE_SMS) exists only in the staging build; the prod/Play build is SMS-free.
    LaunchedEffect(Unit) {
        if (!BuildConfig.SMS_AUTO_CAPTURE) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onIntent(SmsInboxIntent.PermissionChanged(granted))
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(SmsInboxIntent.PermissionChanged(granted))
    }

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
        autoImportAvailable = BuildConfig.SMS_AUTO_CAPTURE,
        onSearchChange = { viewModel.onIntent(SmsInboxIntent.SearchChanged(it)) },
        onDraftChange = { viewModel.onIntent(SmsInboxIntent.DraftChanged(it)) },
        onIngest = { viewModel.onIntent(SmsInboxIntent.IngestDraft) },
        onDelete = { viewModel.onIntent(SmsInboxIntent.Delete(it)) },
        onEnableAutoImport = { permissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
        modifier = modifier,
    )
}
