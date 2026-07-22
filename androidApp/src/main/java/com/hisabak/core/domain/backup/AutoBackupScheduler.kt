package com.hisabak.core.domain.backup

import java.time.ZonedDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toKotlinDuration

/** Auto-backups are anchored to run overnight, after this local hour (24h clock). */
const val AUTO_BACKUP_HOUR = 2

/**
 * Schedules (or cancels) recurring background backups. Domain-level so the policy stays platform-free
 * (Android uses WorkManager today; another platform plugs in its own scheduler behind this).
 */
interface AutoBackupScheduler {
    /** Reconcile the schedule with the current settings: cancel when disabled / [AutoBackupPeriod.NEVER]. */
    fun schedule(period: AutoBackupPeriod, enabled: Boolean)
}

/** The repeat interval for a period, or null when no recurring backup should run. Pure + testable. */
fun autoBackupInterval(period: AutoBackupPeriod): Duration? = when (period) {
    AutoBackupPeriod.NEVER -> null
    AutoBackupPeriod.DAILY -> 1.days
    AutoBackupPeriod.WEEKLY -> 7.days
    AutoBackupPeriod.MONTHLY -> 30.days
}

/** Time from [now] until the next occurrence of [hour]:00 local — used to bias the first run. */
fun delayUntilHour(now: ZonedDateTime, hour: Int): Duration {
    var next = now.toLocalDate().atTime(hour, 0).atZone(now.zone)
    if (!next.isAfter(now)) next = next.plusDays(1)
    return java.time.Duration.between(now, next).toKotlinDuration()
}
