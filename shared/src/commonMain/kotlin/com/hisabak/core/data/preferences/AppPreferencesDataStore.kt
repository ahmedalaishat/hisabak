package com.hisabak.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.ThemeMode
import com.hisabak.core.domain.backup.AutoBackupPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppPreferencesDataStore(private val dataStore: DataStore<Preferences>) : AppPreferences {

    private val onboardingKey = booleanPreferencesKey("onboarding_completed")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")
    private val backupEnabledKey = booleanPreferencesKey("backup_enabled")
    private val backupEncryptionKey = booleanPreferencesKey("backup_encryption_enabled")
    private val autoBackupPeriodKey = stringPreferencesKey("auto_backup_period")
    private val restoreOfferedKey = booleanPreferencesKey("restore_offered")
    private val passphraseConfirmedAtKey = longPreferencesKey("passphrase_confirmed_at")

    override val onboardingCompleted: Flow<Boolean> =
        dataStore.data.map { it[onboardingKey] ?: false }

    override suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { it[onboardingKey] = value }
    }

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs ->
            prefs[themeModeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM
        }

    override suspend fun setThemeMode(value: ThemeMode) {
        dataStore.edit { it[themeModeKey] = value.name }
    }

    override val appLockEnabled: Flow<Boolean> =
        dataStore.data.map { it[appLockKey] ?: false }

    override suspend fun setAppLockEnabled(value: Boolean) {
        dataStore.edit { it[appLockKey] = value }
    }

    override val backupEnabled: Flow<Boolean> =
        dataStore.data.map { it[backupEnabledKey] ?: false }

    override suspend fun setBackupEnabled(value: Boolean) {
        dataStore.edit { it[backupEnabledKey] = value }
    }

    override val backupEncryptionEnabled: Flow<Boolean> =
        dataStore.data.map { it[backupEncryptionKey] ?: false }

    override suspend fun setBackupEncryptionEnabled(value: Boolean) {
        dataStore.edit { it[backupEncryptionKey] = value }
    }

    override val autoBackupPeriod: Flow<AutoBackupPeriod> =
        dataStore.data.map { prefs ->
            prefs[autoBackupPeriodKey]?.let { runCatching { AutoBackupPeriod.valueOf(it) }.getOrNull() }
                ?: AutoBackupPeriod.DEFAULT
        }

    override suspend fun setAutoBackupPeriod(value: AutoBackupPeriod) {
        dataStore.edit { it[autoBackupPeriodKey] = value.name }
    }

    override val restoreOffered: Flow<Boolean> =
        dataStore.data.map { it[restoreOfferedKey] ?: false }

    override suspend fun setRestoreOffered(value: Boolean) {
        dataStore.edit { it[restoreOfferedKey] = value }
    }

    override val passphraseConfirmedAt: Flow<Long> =
        dataStore.data.map { it[passphraseConfirmedAtKey] ?: 0L }

    override suspend fun setPassphraseConfirmedAt(value: Long) {
        dataStore.edit { it[passphraseConfirmedAtKey] = value }
    }
}
