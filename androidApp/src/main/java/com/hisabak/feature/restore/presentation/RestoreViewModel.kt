package com.hisabak.feature.restore.presentation

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabak.core.data.backup.AuthorizeOutcome
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupAccountStore
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.RestoreFromRemoteUseCase
import com.hisabak.core.domain.backup.RestoreResult
import com.hisabak.feature.backup.presentation.SyncPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RestoreMessage {
    data object NothingFound : RestoreMessage
    data class Failed(val error: BackupError) : RestoreMessage
}

data class RestoreUiState(
    val account: BackupAccount? = null,
    val needsPassphrase: Boolean = false,
    val message: RestoreMessage? = null,
    // Non-null while the restore operation is running / done — drives the full-screen SyncScreen.
    val sync: SyncPhase? = null,
)

/**
 * Drives the first-launch "restore from Google Drive?" page: connect an account, look for a backup,
 * and restore it (prompting for the passphrase if it's encrypted). Finishing (restore done or skip)
 * sets `restoreOffered`, which the app's launch gate observes to move on.
 */
class RestoreViewModel(
    private val restoreFromRemote: RestoreFromRemoteUseCase,
    private val authorizer: DriveAuthorizer,
    private val accountStore: BackupAccountStore,
    private val passphraseStore: BackupPassphraseStore,
    private val preferences: AppPreferences,
    private val analytics: Analytics,
) : ViewModel() {

    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()

    fun connect(onNeedConsent: (IntentSender) -> Unit) {
        viewModelScope.launch {
            when (val outcome = authorizer.authorize()) {
                is AuthorizeOutcome.Granted -> onConnected(outcome.account)
                is AuthorizeOutcome.NeedsConsent -> onNeedConsent(outcome.intentSender)
                AuthorizeOutcome.Failed -> _state.update { it.copy(message = RestoreMessage.Failed(BackupError.AuthRequired)) }
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            when (val outcome = authorizer.resultFrom(data)) {
                is AuthorizeOutcome.Granted -> onConnected(outcome.account)
                else -> _state.update { it.copy(message = RestoreMessage.Failed(BackupError.AuthRequired)) }
            }
        }
    }

    fun submitPassphrase(passphrase: String) = attempt(passphrase)

    fun skip() {
        viewModelScope.launch { preferences.setRestoreOffered(true) }
    }

    /** Continue after a successful restore → leave the first-launch flow into the app. */
    fun finishRestore() {
        viewModelScope.launch { preferences.setRestoreOffered(true) }
    }

    private suspend fun onConnected(account: BackupAccount) {
        accountStore.set(account)
        _state.update { it.copy(account = account) }
        attemptNow(null)
    }

    private fun attempt(passphrase: String?) {
        viewModelScope.launch { attemptNow(passphrase) }
    }

    private suspend fun attemptNow(passphrase: String?) {
        _state.update { it.copy(sync = SyncPhase.Running, message = null, needsPassphrase = false) }
        when (val result = restoreFromRemote(passphrase)) {
            RestoreResult.PassphraseRequired ->
                _state.update { it.copy(sync = null, needsPassphrase = true) }
            is RestoreResult.Success -> {
                analytics.log(AnalyticsEvent.BackupRestoreCompleted(true))
                // They restored from a backup, so set them up to keep backing up: turn backup on,
                // and carry over the passphrase + encryption if the backup was encrypted. (The
                // account is already stored from onConnected.)
                preferences.setBackupEnabled(true)
                if (passphrase != null) {
                    passphraseStore.set(passphrase)
                    preferences.setBackupEncryptionEnabled(true)
                } else {
                    preferences.setBackupEncryptionEnabled(false)
                }
                // Show the success screen; Room flows refresh live. Continue → finishRestore().
                _state.update { it.copy(sync = SyncPhase.Done(result.restoredRecords)) }
            }
            RestoreResult.NothingToRestore ->
                _state.update { it.copy(sync = null, message = RestoreMessage.NothingFound) }
            is RestoreResult.Failure -> {
                analytics.log(AnalyticsEvent.BackupRestoreCompleted(false))
                // A wrong passphrase keeps the user on the passphrase entry to retry, rather than
                // bouncing back to the account step (which would silently re-use the same account).
                _state.update {
                    it.copy(
                        sync = null,
                        message = RestoreMessage.Failed(result.error),
                        needsPassphrase = result.error == BackupError.WrongPassphrase,
                    )
                }
            }
        }
    }
}
