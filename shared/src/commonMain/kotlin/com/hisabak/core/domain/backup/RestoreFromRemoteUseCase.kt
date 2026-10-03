package com.hisabak.core.domain.backup

sealed interface RestoreResult {
    data class Success(val restoredRecords: Int) : RestoreResult
    data object NothingToRestore : RestoreResult

    /** The backup is encrypted; call again with the passphrase. */
    data object PassphraseRequired : RestoreResult
    data class Failure(val error: BackupError) : RestoreResult
}

/**
 * Downloads the latest remote backup and replaces all local data with it. If the file is encrypted
 * and no [passphrase] is supplied, returns [RestoreResult.PassphraseRequired] so the UI can prompt.
 */
class RestoreFromRemoteUseCase(
    private val remote: BackupRemote,
    private val restoreFromBytes: RestoreFromBytesUseCase,
) {
    suspend operator fun invoke(passphrase: String?): RestoreResult = try {
        val latest = remote.findLatest()
        if (latest == null) {
            RestoreResult.NothingToRestore
        } else {
            restoreFromBytes(remote.download(latest.id), passphrase)
        }
    } catch (e: BackupException) {
        RestoreResult.Failure(e.error)
    }
}
