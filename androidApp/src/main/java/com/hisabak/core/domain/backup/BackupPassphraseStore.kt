package com.hisabak.core.domain.backup

import kotlinx.coroutines.flow.Flow

/**
 * Stores the backup passphrase as a secret at rest (encrypted via the platform keystore). It's the
 * key for encrypted backups, so it persists while backup is enabled and is cleared when disabled.
 * [get] is for the deferred Drive sync (unattended upload); the UI only needs [isSet]/[set]/[clear].
 */
interface BackupPassphraseStore {
    val isSet: Flow<Boolean>
    suspend fun set(passphrase: String)
    suspend fun get(): String?
    suspend fun clear()
}
