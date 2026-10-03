package com.hisabak.feature.backup.presentation

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.hisabak.core.data.backup.AndroidConsentRequest
import com.hisabak.core.data.backup.AndroidConsentResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.presentation.LaunchedViewEffectHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel

// Opaque bytes: a plain backup is JSON, an encrypted one isn't, and both share the .bak name.
private const val BACKUP_MIME = "application/octet-stream"

@Composable
fun BackupRoute(
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resolver = LocalContext.current.contentResolver

    // The Drive consent screen returns here; hand the result back to the ViewModel.
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onConsentResult(AndroidConsentResult(result.data)) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME),
    ) { uri: Uri? ->
        viewModel.saveExport { bytes ->
            if (uri == null) return@saveExport false
            withContext(Dispatchers.IO) {
                // "wt" truncates, in case the provider hands back an existing file to overwrite.
                checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
            }
            true
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImportFilePicked {
                withContext(Dispatchers.IO) {
                    checkNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
                }
            }
        }
    }

    LaunchedViewEffectHandler(
        effectFlow = viewModel.effect,
        onConsumeEffect = viewModel::consumeEffect,
        onEffect = { effect ->
            when (effect) {
                is BackupEffect.SaveFile -> try {
                    saveLauncher.launch(effect.fileName)
                } catch (e: ActivityNotFoundException) {
                    // No document provider on the device: report it rather than sit on "exporting".
                    viewModel.saveExport { throw e }
                }
            }
        },
    )

    BackupScreen(
        state = state,
        onSetEnabled = viewModel::setEnabled,
        onSetEncryptionEnabled = viewModel::setEncryptionEnabled,
        onSetPassphrase = viewModel::setPassphrase,
        onSetPeriod = viewModel::setAutoBackupPeriod,
        onConnectAccount = {
            viewModel.connect { request ->
                val sender = (request as AndroidConsentRequest).intentSender
                consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
        },
        onBackupNow = viewModel::backupNow,
        onClearError = viewModel::clearError,
        onDismissSync = viewModel::dismissSync,
        onExportFile = viewModel::exportFile,
        onPickImportFile = {
            // Any type: providers label a .bak inconsistently, and the restore rejects a wrong file.
            try {
                openLauncher.launch(arrayOf("*/*"))
            } catch (e: ActivityNotFoundException) {
                viewModel.onImportFilePicked { throw e }
            }
        },
        onConfirmImport = viewModel::confirmImport,
        onSubmitImportPassphrase = viewModel::submitImportPassphrase,
        onCancelImport = viewModel::cancelImport,
        modifier = modifier,
    )
}
