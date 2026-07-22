package com.hisabak.testutil

import com.hisabak.core.domain.backup.BackupPassphraseStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeBackupPassphraseStore : BackupPassphraseStore {
    private val passphrase = MutableStateFlow<String?>(null)

    override val isSet: Flow<Boolean> = passphrase.map { it != null }
    override suspend fun set(passphrase: String) { this.passphrase.value = passphrase }
    override suspend fun get(): String? = passphrase.value
    override suspend fun clear() { passphrase.value = null }
}
