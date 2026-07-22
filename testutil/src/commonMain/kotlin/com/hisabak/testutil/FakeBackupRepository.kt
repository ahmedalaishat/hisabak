package com.hisabak.testutil

import com.hisabak.core.domain.backup.BackupData
import com.hisabak.core.domain.backup.BackupRepository

class FakeBackupRepository(
    var data: BackupData = BackupData(),
) : BackupRepository {
    var replacedWith: BackupData? = null
        private set

    override suspend fun snapshot(): BackupData = data

    override suspend fun replaceAll(data: BackupData) {
        replacedWith = data
        this.data = data
    }
}
