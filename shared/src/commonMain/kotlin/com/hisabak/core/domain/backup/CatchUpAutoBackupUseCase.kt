package com.hisabak.core.domain.backup

import com.hisabak.core.common.Clock
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex

/**
 * Runs a silent auto-backup right now if the last successful backup is older than the configured
 * [AutoBackupPeriod]. Background schedulers can miss runs — iOS's BGAppRefreshTask is purely
 * opportunistic, and WorkManager slips when the device is off — so app launch / foreground calls
 * this as the reliability net. Applies the same rules as the scheduled runs: skip when disabled
 * or [AutoBackupPeriod.NEVER], and never encrypt without the stored passphrase.
 */
class CatchUpAutoBackupUseCase(
    private val preferences: AppPreferences,
    private val passphraseStore: BackupPassphraseStore,
    private val runBackup: RunBackupUseCase,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    private val running = Mutex()

    suspend operator fun invoke() {
        if (!running.tryLock()) return // a catch-up is already in flight
        try {
            if (!preferences.backupEnabled.first()) return
            val interval = autoBackupInterval(preferences.autoBackupPeriod.first()) ?: return
            val elapsed = clock.now().toEpochMilliseconds() - preferences.lastBackupAt.first()
            if (elapsed < interval.inWholeMilliseconds) return
            val passphrase = if (preferences.backupEncryptionEnabled.first()) {
                passphraseStore.get() ?: return // can't encrypt unattended
            } else {
                null
            }
            val result = runBackup(passphrase)
            analytics.log(AnalyticsEvent.BackupRunCompleted(result is BackupRunResult.Success))
        } finally {
            running.unlock()
        }
    }
}
