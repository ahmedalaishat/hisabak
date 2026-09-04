package com.hisabak.core.domain

import com.hisabak.core.domain.backup.AutoBackupPeriod
import kotlinx.coroutines.flow.Flow

/** Lightweight on-device app preferences (DataStore-backed). */
interface AppPreferences {
    /** Whether the user has finished the first-launch onboarding. */
    val onboardingCompleted: Flow<Boolean>

    suspend fun setOnboardingCompleted(value: Boolean)

    /** The chosen appearance; defaults to [ThemeMode.SYSTEM]. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(value: ThemeMode)

    /** Whether the biometric/device-credential app lock is on; defaults to `false`. */
    val appLockEnabled: Flow<Boolean>

    suspend fun setAppLockEnabled(value: Boolean)

    /** Whether Google Drive backup is enabled; defaults to `false`. */
    val backupEnabled: Flow<Boolean>

    suspend fun setBackupEnabled(value: Boolean)

    /** Whether backups are encrypted with a passphrase; defaults to `false` (opt-in). */
    val backupEncryptionEnabled: Flow<Boolean>

    suspend fun setBackupEncryptionEnabled(value: Boolean)

    /** How often automatic backups should run; defaults to [AutoBackupPeriod.DEFAULT]. */
    val autoBackupPeriod: Flow<AutoBackupPeriod>

    suspend fun setAutoBackupPeriod(value: AutoBackupPeriod)

    /** Whether the one-time, post-onboarding "restore from backup?" page has been shown. */
    val restoreOffered: Flow<Boolean>

    suspend fun setRestoreOffered(value: Boolean)

    /** When the backup passphrase was last set or confirmed (epoch millis); `0` if never — drives
     *  the periodic "do you still remember your passphrase?" reminder. */
    val passphraseConfirmedAt: Flow<Long>

    suspend fun setPassphraseConfirmedAt(value: Long)

    /** When a backup last uploaded successfully (epoch millis); `0` if never — drives the
     *  foreground catch-up that covers missed background auto-backup runs. */
    val lastBackupAt: Flow<Long>

    suspend fun setLastBackupAt(value: Long)

    /**
     * Whether unmatched message text may be sent to the parse service; defaults to `false`.
     *
     * This is the consent gate for the only feature that transmits message content off the device,
     * so it is opt-in and nothing reaches the network while it is false.
     */
    val remoteParseEnabled: Flow<Boolean>

    suspend fun setRemoteParseEnabled(value: Boolean)

    /**
     * Whether a verified AI suggestion may become a transaction without a tap; defaults to `false`.
     *
     * Separate from [remoteParseEnabled] on purpose: one decides whether text leaves the phone,
     * this decides whether the app acts unattended. Wanting the second does not imply the first.
     */
    val autoConfirmEnabled: Flow<Boolean>

    suspend fun setAutoConfirmEnabled(value: Boolean)


    /**
     * "Don't ask again" for the SMS-inbox offers, one flag per offer.
     *
     * Only the permanent choice is stored. "Not now" is deliberately in-memory: the user said not
     * now, not never, and a launch is the natural moment to ask once more.
     */
    val suppressedInboxPrompts: Flow<Set<String>>

    suspend fun suppressInboxPrompt(name: String)

    /**
     * The anonymous install id sent with a question so the service can give each install a fair
     * daily allowance. A random UUID minted on first use, tied to nothing, reset by a reinstall.
     */
    val installId: Flow<String?>

    suspend fun setInstallId(value: String)

    /** Questions asked so far on [AskTally.day] (ISO date) — the visible "n of 10 left" counter. */
    val askTally: Flow<AskTally>

    suspend fun setAskTally(value: AskTally)
}

data class AskTally(val day: String, val count: Int) {
    companion object {
        val NONE = AskTally(day = "", count = 0)
    }
}
