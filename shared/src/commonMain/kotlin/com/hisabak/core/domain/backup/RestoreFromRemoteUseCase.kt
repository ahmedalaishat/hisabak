package com.hisabak.core.domain.backup

sealed interface RestoreResult {
    data class Success(val restoredRecords: Int) : RestoreResult
    data object NothingToRestore : RestoreResult

    /** The remote backup is encrypted; call again with the passphrase. */
    data object PassphraseRequired : RestoreResult
    data class Failure(val error: BackupError) : RestoreResult
}

/**
 * Downloads the latest remote backup and replaces all local data with it. If the file is encrypted
 * and no [passphrase] is supplied, returns [RestoreResult.PassphraseRequired] so the UI can prompt.
 */
class RestoreFromRemoteUseCase(
    private val repository: BackupRepository,
    private val codec: BackupCodec,
    private val crypto: BackupCrypto,
    private val remote: BackupRemote,
    private val schemaVersion: Int,
) {
    suspend operator fun invoke(passphrase: String?): RestoreResult = try {
        val latest = remote.findLatest()
        if (latest == null) {
            RestoreResult.NothingToRestore
        } else {
            val bytes = remote.download(latest.id)
            if (crypto.isEncrypted(bytes) && passphrase == null) {
                RestoreResult.PassphraseRequired
            } else {
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
