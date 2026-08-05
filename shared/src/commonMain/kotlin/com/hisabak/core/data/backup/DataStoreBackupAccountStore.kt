package com.hisabak.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupAccountStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreBackupAccountStore(private val dataStore: DataStore<Preferences>) : BackupAccountStore {

    private val emailKey = stringPreferencesKey("backup_account_email")

    override val account: Flow<BackupAccount?> =
        dataStore.data.map { prefs -> prefs[emailKey]?.let(::BackupAccount) }

    override suspend fun set(account: BackupAccount) {
        dataStore.edit { it[emailKey] = account.email }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(emailKey) }
    }
}
