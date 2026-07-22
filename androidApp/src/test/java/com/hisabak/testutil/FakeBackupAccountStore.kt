package com.hisabak.testutil

import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupAccountStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBackupAccountStore : BackupAccountStore {
    private val flow = MutableStateFlow<BackupAccount?>(null)
    override val account: Flow<BackupAccount?> = flow
    override suspend fun set(account: BackupAccount) { flow.value = account }
    override suspend fun clear() { flow.value = null }
}
