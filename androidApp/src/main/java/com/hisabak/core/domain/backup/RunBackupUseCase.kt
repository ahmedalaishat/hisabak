package com.hisabak.core.domain.backup

import com.hisabak.core.common.Clock

sealed interface BackupRunResult {
    data object Success : BackupRunResult
    data class Failure(val error: BackupError) : BackupRunResult
}

/**
 * Snapshots the data, encodes it, optionally encrypts it with [passphrase] (null = no encryption),
 * and uploads it to the remote. The caller resolves the encryption policy + passphrase.
 */
class RunBackupUseCase(
    private val repository: BackupRepository,
    private val codec: BackupCodec,
    private val crypto: BackupCrypto,
    private val remote: BackupRemote,
    private val clock: Clock,
    private val appVersionCode: Int,
    private val schemaVersion: Int,
) {
    suspend operator fun invoke(passphrase: String?): BackupRunResult = try {
        val data = repository.snapshot()
        if (data.totalRecords == 0) {
            BackupRunResult.Failure(BackupError.Empty)
        } else {
            val envelope = BackupEnvelope(
                formatVersion = BACKUP_FORMAT_VERSION,
                schemaVersion = schemaVersion,
                appVersionCode = appVersionCode,
                createdAtMillis = clock.now().toEpochMilli(),
                data = data,
            )
            val encoded = codec.encode(envelope)
            val bytes = if (passphrase != null) crypto.encrypt(encoded, passphrase) else encoded
            remote.upload(bytes)
            BackupRunResult.Success
        }
    } catch (e: BackupException) {
        BackupRunResult.Failure(e.error)
    }
}
