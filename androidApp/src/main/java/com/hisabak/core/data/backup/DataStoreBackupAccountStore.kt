package com.hisabak.core.data.backup

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hisabak.core.domain.backup.BackupAccount
import com.hisabak.core.domain.backup.BackupAccountStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.backupAccountDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "hisabak_backup_account")

class DataStoreBackupAccountStore(private val context: Context) : BackupAccountStore {

    private val emailKey = stringPreferencesKey("backup_account_email")

    override val account: Flow<BackupAccount?> =
        context.backupAccountDataStore.data.map { prefs -> prefs[emailKey]?.let(::BackupAccount) }

    override suspend fun set(account: BackupAccount) {
        context.backupAccountDataStore.edit { it[emailKey] = account.email }
    }

    override suspend fun clear() {
        context.backupAccountDataStore.edit { it.remove(emailKey) }
    }
}
