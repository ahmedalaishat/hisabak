package com.hisabak.core.domain.backup

import kotlinx.serialization.Serializable

/** The current backup wire-format version (the envelope/crypto shape, independent of the DB schema). */
const val BACKUP_FORMAT_VERSION = 1

/**
 * The versioned container that is serialized, then encrypted, into a backup file.
 * [schemaVersion] is the Room schema the data came from (see `HisabakDatabase.SCHEMA_VERSION`); on
 * import it gates compatibility. [appVersionCode] is for diagnostics only.
 */
@Serializable
data class BackupEnvelope(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersionCode: Int,
    val createdAtMillis: Long,
    val data: BackupData,
)
