package com.hisabak.core.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hisabak.core.domain.backup.AUTO_BACKUP_HOUR
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.autoBackupInterval
import com.hisabak.core.domain.backup.delayUntilHour
import java.time.ZonedDateTime
import kotlin.time.toJavaDuration

/** Android [AutoBackupScheduler] backed by WorkManager periodic work (persists across restarts). */
class WorkManagerAutoBackupScheduler(private val context: Context) : AutoBackupScheduler {

    override fun schedule(period: AutoBackupPeriod, enabled: Boolean) {
        val workManager = WorkManager.getInstance(context)
        val interval = autoBackupInterval(period)
        if (!enabled || interval == null) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        // Bias the first run to the next ~02:00 local so backups happen overnight (WorkManager keeps
        // it inexact — Doze/batching can still shift it).
        val request = PeriodicWorkRequestBuilder<BackupWorker>(interval.toJavaDuration())
            .setInitialDelay(delayUntilHour(ZonedDateTime.now(), AUTO_BACKUP_HOUR).toJavaDuration())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    private companion object {
        const val WORK_NAME = "hisabak_auto_backup"
    }
}
