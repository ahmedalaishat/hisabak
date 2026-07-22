package com.hisabak.core.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRunResult
import com.hisabak.core.domain.backup.RunBackupUseCase
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Runs a scheduled backup. A thin Android shell around the platform-free [RunBackupUseCase]; deps
 * come from Koin (default WorkManager factory, no custom factory needed). Silent — the Settings
 * "Last backup" line reflects results.
 */
class BackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params), KoinComponent {

    private val preferences: AppPreferences by inject()
    private val passphraseStore: BackupPassphraseStore by inject()
    private val runBackup: RunBackupUseCase by inject()
    private val analytics: Analytics by inject()

    override suspend fun doWork(): Result {
        if (!preferences.backupEnabled.first() || preferences.autoBackupPeriod.first() == AutoBackupPeriod.NEVER) {
            return Result.success()
        }
        val passphrase = if (preferences.backupEncryptionEnabled.first()) {
            passphraseStore.get() ?: return Result.success() // can't encrypt unattended
        } else {
            null
        }
        return when (val result = runBackup(passphrase)) {
            BackupRunResult.Success -> {
                analytics.log(AnalyticsEvent.BackupRunCompleted(true))
                Result.success()
            }
            is BackupRunResult.Failure -> {
                analytics.log(AnalyticsEvent.BackupRunCompleted(false))
                // Transient (auth/network) → retry with backoff; otherwise give up this run.
                if (result.error == BackupError.AuthRequired || result.error == BackupError.Network) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
        }
    }
}
