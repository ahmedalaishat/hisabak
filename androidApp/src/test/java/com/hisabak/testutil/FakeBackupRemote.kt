package com.hisabak.testutil

import com.hisabak.core.domain.backup.BackupError
import com.hisabak.core.domain.backup.BackupException
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.backup.RemoteBackup

/** In-memory [BackupRemote] holding a single uploaded blob; [failWith] forces an error. */
class FakeBackupRemote : BackupRemote {
    var stored: ByteArray? = null
    var failWith: BackupError? = null

    override suspend fun findLatest(): RemoteBackup? {
        failWith?.let { throw BackupException(it) }
        return stored?.let { RemoteBackup(id = "remote-id", modifiedAtMillis = 1L, sizeBytes = it.size.toLong()) }
    }

    override suspend fun upload(bytes: ByteArray) {
        failWith?.let { throw BackupException(it) }
        stored = bytes
    }

    override suspend fun download(id: String): ByteArray {
        failWith?.let { throw BackupException(it) }
        return stored ?: throw BackupException(BackupError.Network)
    }
}
