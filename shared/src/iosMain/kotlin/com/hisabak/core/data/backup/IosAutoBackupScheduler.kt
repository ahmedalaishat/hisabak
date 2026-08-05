@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hisabak.core.data.backup

import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.CatchUpAutoBackupUseCase
import com.hisabak.core.domain.backup.autoBackupInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGAppRefreshTask
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.dateWithTimeIntervalSinceNow
import org.koin.mp.KoinPlatform

const val AUTO_BACKUP_TASK_ID = "com.hisabak.autobackup"

/**
 * [AutoBackupScheduler] over BGTaskScheduler. iOS refresh tasks are opportunistic — the
 * `earliestBeginDate` is a floor, not a schedule — so backups are best-effort compared to
 * WorkManager's periodic guarantees (accepted; see the Phase B risk register). Each run
 * resubmits the next request, mirroring how periodic work chains.
 */
class IosAutoBackupScheduler : AutoBackupScheduler {

    override fun schedule(period: AutoBackupPeriod, enabled: Boolean) {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(AUTO_BACKUP_TASK_ID)
        val interval = autoBackupInterval(period)
        if (!enabled || interval == null) return
        submit(interval.inWholeSeconds.toDouble())
    }

    private fun submit(delaySeconds: Double) {
        val request = BGAppRefreshTaskRequest(identifier = AUTO_BACKUP_TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(delaySeconds)
        }
        // Throws NSException-style errors via the error pointer; ignore — a failed submit just
        // means no background run until the next foreground reschedule.
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, null)
    }
}

/**
 * Registers the auto-backup task handler. Must run before the app finishes launching —
 * called from `iOSApp.swift`'s init via `startIosApp`. The backup itself is the shared
 * [CatchUpAutoBackupUseCase] (same rules as the foreground catch-up, and skips when a
 * recent foreground backup already covered this window).
 */
internal fun registerAutoBackupTask(appScope: CoroutineScope) {
    BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
        identifier = AUTO_BACKUP_TASK_ID,
        usingQueue = null,
    ) { task ->
        val refreshTask = task as BGAppRefreshTask
        val job = appScope.launch {
            val koin = KoinPlatform.getKoin()
            val preferences = koin.get<AppPreferences>()
            // Chain the next run first so a crash mid-backup doesn't silently stop the schedule.
            koin.get<AutoBackupScheduler>().schedule(
                preferences.autoBackupPeriod.first(),
                preferences.backupEnabled.first(),
            )
            koin.get<CatchUpAutoBackupUseCase>().invoke()
        }
        job.invokeOnCompletion { refreshTask.setTaskCompletedWithSuccess(it == null) }
        refreshTask.expirationHandler = { job.cancel() }
    }
}
