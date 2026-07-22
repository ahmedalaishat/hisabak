package com.hisabak.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path

const val APP_PREFS_STORE = "hisabak_prefs"
const val BACKUP_ACCOUNT_STORE = "hisabak_backup_account"

fun createPreferencesDataStore(producePath: () -> Path): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = producePath)
