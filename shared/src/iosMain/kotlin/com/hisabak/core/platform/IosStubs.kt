package com.hisabak.core.platform

import com.hisabak.core.data.backup.AuthorizeOutcome
import com.hisabak.core.data.backup.ConsentResult
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.domain.backup.AutoBackupPeriod
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.RemoteBackup
import com.hisabak.core.domain.security.AuthAvailability
import com.hisabak.core.domain.security.BiometricAvailability
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.domain.TransactionRecordedAlert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

// iOS Phase A stubs for the platform ports androidApp binds natively. Silent no-ops are only used
// where dropping the effect is harmless (analytics, OS notifications, background scheduling);
// anything whose silent failure would corrupt or lose data throws instead.

/** TODO(Phase-B): real iOS analytics (Firebase via gitlive, or keep as no-op). */
class NoopAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
    override fun setCurrentScreen(name: String) = Unit
}

/** TODO(Phase-B): UNUserNotificationCenter-backed notifier. */
class NoopNotifier : Notifier {
    override fun post(notification: Notification) = Unit
    override fun postTransactionRecorded(alert: TransactionRecordedAlert) = Unit
}

/** TODO(Phase-B): localized notification copy once iOS posts notifications. */
class NoopNotificationStrings : NotificationStrings {
    override fun transactionRecordedTitle(): String = ""
    override fun transactionRecorded(amount: String, brand: String, category: String): String = ""
    override fun transactionRecordedUncategorized(amount: String, brand: String): String = ""
    override fun budgetReachedTitle(category: String): String = ""
    override fun budgetLevelTitle(category: String, level: Int): String = ""
    override fun budgetMessage(spent: String, limit: String): String = ""
}

/** TODO(Phase-B): BGTaskScheduler-backed auto-backup. */
class NoopAutoBackupScheduler : AutoBackupScheduler {
    override fun schedule(period: AutoBackupPeriod, enabled: Boolean) = Unit
}

/** TODO(Phase-B): CryptoKit AES-GCM. Throws — a silent no-op would corrupt backups. */
class UnsupportedBackupCrypto : BackupCrypto {
    override fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray =
        throw NotImplementedError("Backup encryption is not available on iOS yet. TODO(Phase-B)")

    override fun decrypt(ciphertext: ByteArray, passphrase: String): ByteArray =
        throw NotImplementedError("Backup decryption is not available on iOS yet. TODO(Phase-B)")

    override fun isEncrypted(bytes: ByteArray): Boolean =
        throw NotImplementedError("Backup encryption is not available on iOS yet. TODO(Phase-B)")
}

/** TODO(Phase-B): Keychain-backed passphrase store. Throws — plaintext fallback is not acceptable. */
class UnsupportedBackupPassphraseStore : BackupPassphraseStore {
    override val isSet: Flow<Boolean> = flowOf(false)

    override suspend fun set(passphrase: String): Unit =
        throw NotImplementedError("The backup passphrase store is not available on iOS yet. TODO(Phase-B)")

    override suspend fun get(): String? =
        throw NotImplementedError("The backup passphrase store is not available on iOS yet. TODO(Phase-B)")

    override suspend fun clear(): Unit =
        throw NotImplementedError("The backup passphrase store is not available on iOS yet. TODO(Phase-B)")
}

/** TODO(Phase-B): LocalAuthentication-backed availability + prompt. */
class UnavailableBiometricAvailability : BiometricAvailability {
    override fun availability(): AuthAvailability = AuthAvailability.Unavailable
}

/** TODO(Phase-B): ASWebAuthenticationSession OAuth for Drive. */
class UnavailableDriveAuthorizer : DriveAuthorizer {
    override suspend fun authorize(): AuthorizeOutcome = AuthorizeOutcome.Unavailable

    override fun resultFrom(result: ConsentResult?): AuthorizeOutcome = AuthorizeOutcome.Unavailable

    override suspend fun accessToken(): String =
        throw NotImplementedError("Drive authorization is not available on iOS yet. TODO(Phase-B)")
}

/** TODO(Phase-B): Ktor-based Drive remote. Throws — the UI never reaches it while authorization
 *  reports [AuthorizeOutcome.Unavailable]. */
class UnsupportedBackupRemote : BackupRemote {
    override suspend fun findLatest(): RemoteBackup? =
        throw NotImplementedError("The Drive backup remote is not available on iOS yet. TODO(Phase-B)")

    override suspend fun upload(bytes: ByteArray): Unit =
        throw NotImplementedError("The Drive backup remote is not available on iOS yet. TODO(Phase-B)")

    override suspend fun download(id: String): ByteArray =
        throw NotImplementedError("The Drive backup remote is not available on iOS yet. TODO(Phase-B)")
}
