package com.hisabak.feature.backup.presentation

import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.data.backup.AuthorizeOutcome
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupAccountStore
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.BackupRunResult
import com.hisabak.core.domain.backup.RemoteBackup
import com.hisabak.core.domain.backup.RunBackupUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BackupUiState(
    // False until the persisted settings have loaded — avoids flashing the "off" UI on first frame.
    val ready: Boolean = false,
    val enabled: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val passphraseSet: Boolean = false,
    val period: AutoBackupPeriod = AutoBackupPeriod.DEFAULT,
    val account: BackupAccount? = null,
    // The most recent backup in Drive (for the "last backup … · size" line), null if none/unknown.
    val lastBackup: RemoteBackup? = null,
    // A pre-flight inline error (no account / no passphrase) shown on the settings screen.
    val error: BackupError? = null,
    // Non-null while the backup operation is running / finished — drives the full-screen SyncScreen.
    val sync: SyncPhase? = null,
)

private data class Settings(
    val enabled: Boolean,
    val encryptionEnabled: Boolean,
    val passphraseSet: Boolean,
    val period: AutoBackupPeriod,
)

private data class Transient(
    val error: BackupError? = null,
    val sync: SyncPhase? = null,
    val lastBackup: RemoteBackup? = null,
)

class BackupViewModel(
    private val preferences: AppPreferences,
    private val passphraseStore: BackupPassphraseStore,
    private val accountStore: BackupAccountStore,
    private val authorizer: DriveAuthorizer,
    private val runBackup: RunBackupUseCase,
    private val remote: BackupRemote,
    private val scheduler: AutoBackupScheduler,
    private val clock: Clock,
    private val analytics: Analytics,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<BackupUiState> = combine(
        combine(
            preferences.backupEnabled,
            preferences.backupEncryptionEnabled,
            passphraseStore.isSet,
            preferences.autoBackupPeriod,
        ) { enabled, encryption, passphraseSet, period -> Settings(enabled, encryption, passphraseSet, period) },
        accountStore.account,
        transient,
    ) { s, account, t ->
        BackupUiState(
            ready = true,
            enabled = s.enabled,
            encryptionEnabled = s.encryptionEnabled,
            passphraseSet = s.passphraseSet,
            period = s.period,
            account = account,
            lastBackup = t.lastBackup,
            error = t.error,
            sync = t.sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState())

    init {
        // Self-heal: encryption can't be on without a passphrase (e.g. the app was killed while the
        // set-passphrase sheet was open). Revert to off so the state is always consistent.
        viewModelScope.launch {
            if (preferences.backupEncryptionEnabled.first() && !passphraseStore.isSet.first()) {
                preferences.setBackupEncryptionEnabled(false)
            }
        }
        // When an account is connected, fetch the latest backup's date/size for the status line.
        viewModelScope.launch {
            accountStore.account.collect { account ->
                if (account != null) refreshLastBackup() else transient.update { it.copy(lastBackup = null) }
            }
        }
    }

    private suspend fun refreshLastBackup() {
        val latest = runCatching { remote.findLatest() }.getOrNull()
        transient.update { it.copy(lastBackup = latest) }
    }

    fun setEnabled(enabled: Boolean) {
        analytics.log(AnalyticsEvent.BackupToggled(enabled))
        viewModelScope.launch {
            preferences.setBackupEnabled(enabled)
            // Turning backup off clears the passphrase, so encryption must go off too (it can't be
            // on without a passphrase). Re-enabling then starts unencrypted until the user opts in.
            if (!enabled) {
                passphraseStore.clear()
                preferences.setBackupEncryptionEnabled(false)
            }
            scheduler.schedule(preferences.autoBackupPeriod.first(), enabled)
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        analytics.log(AnalyticsEvent.BackupEncryptionToggled(enabled))
        viewModelScope.launch {
            preferences.setBackupEncryptionEnabled(enabled)
            if (!enabled) passphraseStore.clear()
        }
    }

    /** Saves the passphrase and turns encryption on atomically, so encryption is only ever persisted
     *  on once a passphrase exists. */
    fun setPassphrase(passphrase: String) {
        viewModelScope.launch {
            passphraseStore.set(passphrase)
            preferences.setBackupEncryptionEnabled(true)
            preferences.setPassphraseConfirmedAt(clock.now().toEpochMilli())
        }
    }

    fun setAutoBackupPeriod(period: AutoBackupPeriod) {
        analytics.log(AnalyticsEvent.AutoBackupPeriodSet(period.name.lowercase()))
        viewModelScope.launch {
            preferences.setAutoBackupPeriod(period)
            scheduler.schedule(period, preferences.backupEnabled.first())
        }
    }

    /** Begins the account connect flow; [onNeedConsent] launches the consent UI for a result. */
    fun connect(onNeedConsent: (IntentSender) -> Unit) {
        viewModelScope.launch {
            when (val outcome = authorizer.authorize()) {
                is AuthorizeOutcome.Granted -> connected(outcome.account)
                is AuthorizeOutcome.NeedsConsent -> onNeedConsent(outcome.intentSender)
                AuthorizeOutcome.Failed -> fail(BackupError.AuthRequired)
            }
        }
    }

    fun onConsentResult(data: Intent?) {
        viewModelScope.launch {
            when (val outcome = authorizer.resultFrom(data)) {
                is AuthorizeOutcome.Granted -> connected(outcome.account)
                else -> fail(BackupError.AuthRequired)
            }
        }
    }

    fun backupNow() {
        viewModelScope.launch {
            if (accountStore.account.first() == null) {
                fail(BackupError.AuthRequired)
                return@launch
            }
            val passphrase = if (preferences.backupEncryptionEnabled.first()) {
                passphraseStore.get() ?: run { fail(BackupError.PassphraseRequired); return@launch }
            } else {
                null
            }
            transient.update { it.copy(error = null, sync = SyncPhase.Running) }
            val result = runBackup(passphrase)
            analytics.log(AnalyticsEvent.BackupRunCompleted(result is BackupRunResult.Success))
            val last = transient.value.lastBackup
            transient.value = Transient(
                lastBackup = last,
                sync = when (result) {
                    BackupRunResult.Success -> SyncPhase.Done()
                    is BackupRunResult.Failure -> SyncPhase.Failed(result.error)
                },
            )
            if (result is BackupRunResult.Success) refreshLastBackup()
        }
    }

    /** Leaves the sync screen (Continue / Close) back to the settings. */
    fun dismissSync() = transient.update { it.copy(sync = null) }

    fun clearError() = transient.update { it.copy(error = null) }

    private suspend fun connected(account: BackupAccount) {
        accountStore.set(account)
        analytics.log(AnalyticsEvent.BackupAccountConnected)
    }

    private fun fail(error: BackupError) = transient.update { it.copy(error = error) }
}
