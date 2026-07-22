package com.hisabak.core.domain.analytics

import com.hisabak.core.common.Money

/**
 * The catalogue of product-analytics events. Centralizing every event name and parameter shape here
 * keeps the strict **no-PII** rule enforceable in one place: events only ever carry booleans, enums,
 * and coarse buckets — never raw amounts, notes, names, or SMS text. Feature-specific enums are
 * stringified by the caller (so this file, in `core`, stays free of feature dependencies).
 */
sealed class AnalyticsEvent(
    val name: String,
    val params: Map<String, Any?> = emptyMap(),
) {
    /** First-run activation: the user finished onboarding. */
    data object OnboardingCompleted : AnalyticsEvent("onboarding_completed")

    /** A transaction the user added by hand (SMS-captured ones fire [SmsCaptured] instead). */
    class TransactionCreated(amount: Money, hasNote: Boolean) : AnalyticsEvent(
        name = "transaction_created",
        params = mapOf("source" to "manual", "amount_bucket" to amountBucket(amount), "has_note" to hasNote),
    )

    class TransactionEdited(amount: Money) : AnalyticsEvent(
        name = "transaction_edited",
        params = mapOf("amount_bucket" to amountBucket(amount)),
    )

    data object TransactionDeleted : AnalyticsEvent("transaction_deleted")

    /** A bank message was parsed into a transaction. [source] is a [CaptureSource] name, lowercased. */
    class SmsCaptured(source: String, amount: Money) : AnalyticsEvent(
        name = "sms_captured",
        params = mapOf("source" to source, "amount_bucket" to amountBucket(amount)),
    )

    /** A capture attempt failed. [reason] is the domain-error type (never the raw message text). */
    class SmsParseFailed(reason: String) : AnalyticsEvent(
        name = "sms_parse_failed",
        params = mapOf("reason" to reason),
    )

    /** [type] is a [CategoryType] name, lowercased. */
    class CategoryCreated(type: String, hasLimit: Boolean) : AnalyticsEvent(
        name = "category_created",
        params = mapOf("type" to type, "has_limit" to hasLimit),
    )

    class BrandCreated(hasCategory: Boolean) : AnalyticsEvent(
        name = "brand_created",
        params = mapOf("has_category" to hasCategory),
    )

    data object BrandMerged : AnalyticsEvent("brand_merged")

    /** [period] is a [SummaryPeriod] name, lowercased. */
    class DashboardPeriodChanged(period: String) : AnalyticsEvent(
        name = "dashboard_period_changed",
        params = mapOf("period" to period),
    )

    /** [mode] is a [com.hisabak.core.domain.ThemeMode] name, lowercased. */
    class SettingsThemeChanged(mode: String) : AnalyticsEvent(
        name = "settings_theme_changed",
        params = mapOf("mode" to mode),
    )

    /** [language] is a BCP-47 tag (e.g. "en", "ar"). */
    class SettingsLanguageChanged(language: String) : AnalyticsEvent(
        name = "settings_language_changed",
        params = mapOf("language" to language),
    )

    /** The user turned the biometric/device-credential app lock on or off. */
    class AppLockToggled(enabled: Boolean) : AnalyticsEvent(
        name = "app_lock_toggled",
        params = mapOf("enabled" to enabled),
    )

    /** The user turned Google Drive backup on or off. */
    class BackupToggled(enabled: Boolean) : AnalyticsEvent(
        name = "backup_toggled",
        params = mapOf("enabled" to enabled),
    )

    /** The user turned backup encryption on or off. */
    class BackupEncryptionToggled(enabled: Boolean) : AnalyticsEvent(
        name = "backup_encryption_toggled",
        params = mapOf("enabled" to enabled),
    )

    /** The user chose an auto-backup period. [period] is an [AutoBackupPeriod] name, lowercased. */
    class AutoBackupPeriodSet(period: String) : AnalyticsEvent(
        name = "auto_backup_period_set",
        params = mapOf("period" to period),
    )

    /** A Google account was connected for backup (no identifier — boolean signal only). */
    data object BackupAccountConnected : AnalyticsEvent("backup_account_connected")

    /** A manual "back up now" finished. */
    class BackupRunCompleted(success: Boolean) : AnalyticsEvent(
        name = "backup_run_completed",
        params = mapOf("success" to success),
    )

    /** A restore-from-Drive finished. */
    class BackupRestoreCompleted(success: Boolean) : AnalyticsEvent(
        name = "backup_restore_completed",
        params = mapOf("success" to success),
    )
}

/** Coarse, non-reversible magnitude bucket of a money value — never the raw amount. */
private fun amountBucket(amount: Money): String {
    val major = kotlin.math.abs(amount.amountMinor) / 100.0
    return when {
        major < 50 -> "under_50"
        major < 200 -> "50_200"
        major < 1_000 -> "200_1k"
        major < 5_000 -> "1k_5k"
        else -> "over_5k"
    }
}
