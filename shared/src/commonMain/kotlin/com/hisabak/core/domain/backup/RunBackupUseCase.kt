package com.hisabak.core.domain.backup

import com.hisabak.core.common.Clock
import com.hisabak.core.domain.AppPreferences

sealed interface BackupRunResult {
    data object Success : BackupRunResult
    data class Failure(val error: BackupError) : BackupRunResult
}

/**
 * Builds the backup bytes (encrypted with [passphrase], null = no encryption) and uploads them to
 * the remote. The caller resolves the encryption policy + passphrase. A successful upload stamps
 * [AppPreferences.lastBackupAt], which the foreground catch-up reads — so only an upload stamps
 * it; a file export doesn't put a copy in Drive.
 */
class RunBackupUseCase(
    private val buildBytes: BuildBackupBytesUseCase,
    private val remote: BackupRemote,
    private val clock: Clock,
    private val preferences: AppPreferences,
) {
    suspend operator fun invoke(passphrase: String?): BackupRunResult = try {
        when (val built = buildBytes(passphrase)) {
            is BackupBytesResult.Failure -> BackupRunResult.Failure(built.error)
            is BackupBytesResult.Success -> {
                remote.upload(built.bytes)
                preferences.setLastBackupAt(clock.now().toEpochMilliseconds())
                BackupRunResult.Success
            }
        }
    } catch (e: BackupException) {
        BackupRunResult.Failure(e.error)
    }
}
