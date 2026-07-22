package com.hisabak.core.domain.backup

/** Metadata for the backup stored remotely (Drive App Data Folder holds a single, overwritten file). */
data class RemoteBackup(
    val id: String,
    val modifiedAtMillis: Long,
    val sizeBytes: Long,
)

/**
 * A remote destination for backup bytes. Drive-backed today; the bytes are opaque (already
 * encoded/encrypted), keeping this destination-agnostic. Throws [BackupException] on failure
 * ([BackupError.AuthRequired] / [BackupError.Network]).
 */
interface BackupRemote {
    suspend fun findLatest(): RemoteBackup?
    suspend fun upload(bytes: ByteArray)
    suspend fun download(id: String): ByteArray
}
