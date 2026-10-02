package com.hisabak.core.domain.backup

/**
 * Replaces all local data with the backup in [bytes] — a Drive download or a file the user picked.
 * An encrypted file with no [passphrase] returns [RestoreResult.PassphraseRequired] so the UI can
 * prompt; nothing is written unless the whole file decrypts, decodes, and passes the schema gate.
 */
class RestoreFromBytesUseCase(
    private val repository: BackupRepository,
    private val codec: BackupCodec,
    private val crypto: BackupCrypto,
    private val schemaVersion: Int,
) {
    suspend operator fun invoke(bytes: ByteArray, passphrase: String?): RestoreResult = try {
        when {
            bytes.isEmpty() -> RestoreResult.Failure(BackupError.Empty)
            crypto.isEncrypted(bytes) && passphrase == null -> RestoreResult.PassphraseRequired
            else -> {
                val decoded = if (crypto.isEncrypted(bytes)) crypto.decrypt(bytes, passphrase!!) else bytes
                val envelope = codec.decode(decoded)
                if (envelope.schemaVersion > schemaVersion) {
                    RestoreResult.Failure(BackupError.UnsupportedVersion(envelope.schemaVersion, schemaVersion))
                } else {
                    repository.replaceAll(envelope.data)
                    RestoreResult.Success(envelope.data.totalRecords)
                }
            }
        }
    } catch (e: BackupException) {
        RestoreResult.Failure(e.error)
    }
}
