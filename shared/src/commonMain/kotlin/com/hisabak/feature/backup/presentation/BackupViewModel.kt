package com.hisabak.feature.backup.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.data.backup.AuthorizeOutcome
import com.hisabak.core.data.backup.ConsentRequest
import com.hisabak.core.data.backup.ConsentResult
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupAccountStore
import com.hisabak.core.domain.backup.BackupBytesResult
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.BackupRunResult
import com.hisabak.core.domain.backup.BuildBackupBytesUseCase
import com.hisabak.core.domain.backup.RemoteBackup
import com.hisabak.core.domain.backup.RestoreFromBytesUseCase
import com.hisabak.core.domain.backup.RestoreResult
import com.hisabak.core.domain.backup.RunBackupUseCase
import com.hisabak.core.domain.backup.analyticsKind
import com.hisabak.core.domain.backup.exportFileName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    // Which operation [sync] is reporting on: a Drive backup, a file export, or a file import.
    val syncKind: SyncKind = SyncKind.BackUp,
    // True from the export tap until the platform saver reports back, so it can't be tapped twice.
    val exporting: Boolean = false,
    // Non-null while a picked backup file waits on the user: the replace confirmation, then a
    // passphrase if the file turns out to be encrypted.
    val importStep: ImportStep? = null,
)

sealed interface ImportStep {
    data object Confirm : ImportStep

    /** The file is encrypted. [error] is [BackupError.WrongPassphrase] after a failed attempt. */
    data class Passphrase(val error: BackupError? = null) : ImportStep
}

/** One-shot asks the platform layer answers: open the system "save as" for [fileName]. */
sealed interface BackupEffect {
    data class SaveFile(val fileName: String) : BackupEffect
}

private data class Settings(
    val enabled: Boolean,
    val encryptionEnabled: Boolean,
    val passphraseSet: Boolean,
    val period: AutoBackupPeriod,
)

private data class Transient(
    val error: BackupError? = null,
    val sync: SyncPhase? = null,
    val syncKind: SyncKind = SyncKind.BackUp,
    val lastBackup: RemoteBackup? = null,
    val exporting: Boolean = false,
    val importStep: ImportStep? = null,
)

class BackupViewModel(
    private val preferences: AppPreferences,
    private val passphraseStore: BackupPassphraseStore,
    private val accountStore: BackupAccountStore,
    private val authorizer: DriveAuthorizer,
    private val runBackup: RunBackupUseCase,
    private val buildBackupBytes: BuildBackupBytesUseCase,
    private val restoreFromBytes: RestoreFromBytesUseCase,
    private val remote: BackupRemote,
    private val scheduler: AutoBackupScheduler,
    private val clock: Clock,
    private val flavor: String,
    private val analytics: Analytics,
) : ViewModel() {

    private val transient = MutableStateFlow(Transient())

    private val _effect = MutableStateFlow<BackupEffect?>(null)
    val effect: StateFlow<BackupEffect?> = _effect.asStateFlow()

    // Held here rather than in the UI state: the bytes are the user's whole ledger, and nothing
    // on screen needs them — only the platform saver and the restore do.
    private var exportBytes: ByteArray? = null
    private var exportEncrypted = false
    private var importBytes: ByteArray? = null

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
            syncKind = t.syncKind,
            exporting = t.exporting,
            importStep = t.importStep,
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
            preferences.setPassphraseConfirmedAt(clock.now().toEpochMilliseconds())
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
    fun connect(onNeedConsent: (ConsentRequest) -> Unit) {
        viewModelScope.launch {
            when (val outcome = authorizer.authorize()) {
                is AuthorizeOutcome.Granted -> connected(outcome.account)
                is AuthorizeOutcome.NeedsConsent -> onNeedConsent(outcome.request)
                AuthorizeOutcome.Unavailable,
                AuthorizeOutcome.Failed,
                -> fail(BackupError.AuthRequired)
            }
        }
    }

    fun onConsentResult(result: ConsentResult?) {
        viewModelScope.launch {
            when (val outcome = authorizer.resultFrom(result)) {
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
            transient.update { it.copy(error = null, sync = SyncPhase.Running, syncKind = SyncKind.BackUp) }
            val result = runBackup(passphrase)
            analytics.log(AnalyticsEvent.BackupRunCompleted(result is BackupRunResult.Success))
            // A dead authorization means the account is no longer connected — clear it so the
            // Connect button comes back. Otherwise the screen keeps saying "connect an account"
            // while hiding the only way to do so.
            if (result is BackupRunResult.Failure && result.error == BackupError.AuthRequired) {
                accountStore.clear()
            }
            transient.update {
                it.copy(
                    error = null,
                    sync = when (result) {
                        BackupRunResult.Success -> SyncPhase.Done()
                        is BackupRunResult.Failure -> SyncPhase.Failed(result.error)
                    },
                )
            }
            if (result is BackupRunResult.Success) refreshLastBackup()
        }
    }

    /**
     * Builds the backup file — the same bytes a Drive backup uploads, encrypted when backup
     * encryption is on — then asks the platform to save it via [BackupEffect.SaveFile]. Needs no
     * account and no network, and deliberately leaves `lastBackupAt` alone: a file on the phone
     * isn't a copy in Drive, and the auto-backup catch-up must keep seeing Drive's age.
     */
    fun exportFile() {
        if (transient.value.exporting) return
        transient.update { it.copy(error = null, exporting = true) }
        viewModelScope.launch {
            val passphrase = if (preferences.backupEncryptionEnabled.first()) {
                passphraseStore.get() ?: run {
                    transient.update { it.copy(exporting = false, error = BackupError.PassphraseRequired) }
                    return@launch
                }
            } else {
                null
            }
            when (val built = buildBackupBytes(passphrase)) {
                is BackupBytesResult.Failure -> {
                    analytics.log(AnalyticsEvent.BackupFileExported(false, passphrase != null, built.error.analyticsKind))
                    transient.update { it.copy(exporting = false, error = built.error) }
                }
                is BackupBytesResult.Success -> {
                    exportBytes = built.bytes
                    exportEncrypted = passphrase != null
                    _effect.value = BackupEffect.SaveFile(exportFileName(flavor, clock.today()))
                }
            }
        }
    }

    /**
     * The platform's answer to [BackupEffect.SaveFile]. [save] writes the bytes where the user
     * chose, returning false if they cancelled the picker; a throw means the write failed.
     */
    fun saveExport(save: suspend (ByteArray) -> Boolean) {
        val bytes = exportBytes ?: return
        exportBytes = null
        viewModelScope.launch {
            val saved = try {
                save(bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            when (saved) {
                false -> transient.update { it.copy(exporting = false) }
                true -> {
                    analytics.log(AnalyticsEvent.BackupFileExported(true, exportEncrypted))
                    transient.update { it.copy(exporting = false, syncKind = SyncKind.Export, sync = SyncPhase.Done()) }
                }
                null -> {
                    val error = BackupError.FileAccess
                    analytics.log(AnalyticsEvent.BackupFileExported(false, exportEncrypted, error.analyticsKind))
                    transient.update { it.copy(exporting = false, syncKind = SyncKind.Export, sync = SyncPhase.Failed(error)) }
                }
            }
        }
    }

    fun consumeEffect() {
        _effect.value = null
    }

    /**
     * The user picked a backup file; [read] returns its bytes (and throws if it can't). Nothing is
     * replaced yet — the screen asks for confirmation first, since an import overwrites everything.
     */
    fun onImportFilePicked(read: suspend () -> ByteArray) {
        viewModelScope.launch {
            val bytes = try {
                read()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (bytes == null) {
                val error = BackupError.FileAccess
                analytics.log(AnalyticsEvent.BackupFileImported(false, error.analyticsKind))
                transient.update { it.copy(error = error) }
            } else {
                importBytes = bytes
                transient.update { it.copy(error = null, importStep = ImportStep.Confirm) }
            }
        }
    }

    fun confirmImport() = runImport(passphrase = null)

    fun submitImportPassphrase(passphrase: String) = runImport(passphrase)

    fun cancelImport() {
        importBytes = null
        transient.update { it.copy(importStep = null) }
    }

    private fun runImport(passphrase: String?) {
        val bytes = importBytes ?: return
        transient.update { it.copy(importStep = null, syncKind = SyncKind.Import, sync = SyncPhase.Running) }
        viewModelScope.launch {
            when (val result = restoreFromBytes(bytes, passphrase)) {
                RestoreResult.PassphraseRequired ->
                    transient.update { it.copy(sync = null, importStep = ImportStep.Passphrase()) }
                is RestoreResult.Success -> {
                    importBytes = null
                    analytics.log(AnalyticsEvent.BackupFileImported(true))
                    transient.update { it.copy(sync = SyncPhase.Done(result.restoredRecords)) }
                }
                // Restoring from bytes never looks anything up, so there is no "nothing found".
                RestoreResult.NothingToRestore -> importFailed(BackupError.Empty)
                is RestoreResult.Failure -> importFailed(result.error)
            }
        }
    }

    private fun importFailed(error: BackupError) {
        analytics.log(AnalyticsEvent.BackupFileImported(false, error.analyticsKind))
        if (error == BackupError.WrongPassphrase) {
            // Keep the file and stay on the passphrase prompt so the user can try again.
            transient.update { it.copy(sync = null, importStep = ImportStep.Passphrase(error)) }
        } else {
            importBytes = null
            transient.update { it.copy(sync = SyncPhase.Failed(error)) }
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
