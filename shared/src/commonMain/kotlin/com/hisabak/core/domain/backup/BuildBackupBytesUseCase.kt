package com.hisabak.core.domain.backup

import com.hisabak.core.common.Clock

sealed interface BackupBytesResult {
    class Success(val bytes: ByteArray) : BackupBytesResult
    data class Failure(val error: BackupError) : BackupBytesResult
}

/**
 * Snapshots the data into a backup file's bytes: envelope → encode → encrypt with [passphrase]
 * (null = plain). Destination-agnostic, so the Drive upload and a file export write identical
 * bytes and either restores through [RestoreFromBytesUseCase].
 */
class BuildBackupBytesUseCase(
    private val repository: BackupRepository,
    private val codec: BackupCodec,
    private val crypto: BackupCrypto,
    private val clock: Clock,
    private val appVersionCode: Int,
    private val schemaVersion: Int,
) {
    suspend operator fun invoke(passphrase: String?): BackupBytesResult = try {
        val data = repository.snapshot()
        if (data.totalRecords == 0) {
            BackupBytesResult.Failure(BackupError.Empty)
        } else {
            val envelope = BackupEnvelope(
                formatVersion = BACKUP_FORMAT_VERSION,
                schemaVersion = schemaVersion,
                appVersionCode = appVersionCode,
                createdAtMillis = clock.now().toEpochMilliseconds(),
                data = data,
            )
            val encoded = codec.encode(envelope)
            BackupBytesResult.Success(if (passphrase != null) crypto.encrypt(encoded, passphrase) else encoded)
        }
    } catch (e: BackupException) {
        BackupBytesResult.Failure(e.error)
    }
}
