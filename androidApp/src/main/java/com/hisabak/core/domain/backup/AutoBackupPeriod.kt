package com.hisabak.core.domain.backup

/** How often automatic Google Drive backups run. Scheduling itself lands in a later PR. */
enum class AutoBackupPeriod {
    NEVER,
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    companion object {
        // Auto-backup is opt-in: off until the user picks a frequency.
        val DEFAULT = NEVER
    }
}
