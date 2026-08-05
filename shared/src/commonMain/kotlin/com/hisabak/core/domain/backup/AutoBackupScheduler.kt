package com.hisabak.core.domain.backup

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

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

/** Time from [now] until the next occurrence of [hour]:00 local in [zone] — biases the first run. */
fun delayUntilHour(now: Instant, zone: TimeZone, hour: Int): Duration {
    val today = now.toLocalDateTime(zone).date
    val target = LocalTime(hour, 0)
    var next = today.atTime(target).toInstant(zone)
    if (next <= now) next = today.plus(1, DateTimeUnit.DAY).atTime(target).toInstant(zone)
    return next - now
}
