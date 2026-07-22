package com.hisabak.testutil

import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.ThemeMode
import com.hisabak.core.domain.backup.AutoBackupPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppPreferences(
    initial: Boolean = false,
    initialThemeMode: ThemeMode = ThemeMode.SYSTEM,
) : AppPreferences {
    private val flow = MutableStateFlow(initial)
    override val onboardingCompleted: Flow<Boolean> = flow
    override suspend fun setOnboardingCompleted(value: Boolean) { flow.value = value }

    private val themeFlow = MutableStateFlow(initialThemeMode)
    override val themeMode: Flow<ThemeMode> = themeFlow
    override suspend fun setThemeMode(value: ThemeMode) { themeFlow.value = value }

    private val appLockFlow = MutableStateFlow(false)
    override val appLockEnabled: Flow<Boolean> = appLockFlow
    override suspend fun setAppLockEnabled(value: Boolean) { appLockFlow.value = value }

    private val backupFlow = MutableStateFlow(false)
    override val backupEnabled: Flow<Boolean> = backupFlow
    override suspend fun setBackupEnabled(value: Boolean) { backupFlow.value = value }

    private val encryptionFlow = MutableStateFlow(false)
    override val backupEncryptionEnabled: Flow<Boolean> = encryptionFlow
    override suspend fun setBackupEncryptionEnabled(value: Boolean) { encryptionFlow.value = value }

    private val periodFlow = MutableStateFlow(AutoBackupPeriod.DEFAULT)
    override val autoBackupPeriod: Flow<AutoBackupPeriod> = periodFlow
    override suspend fun setAutoBackupPeriod(value: AutoBackupPeriod) { periodFlow.value = value }

    private val restoreOfferedFlow = MutableStateFlow(false)
    override val restoreOffered: Flow<Boolean> = restoreOfferedFlow
    override suspend fun setRestoreOffered(value: Boolean) { restoreOfferedFlow.value = value }

    private val passphraseConfirmedAtFlow = MutableStateFlow(0L)
    override val passphraseConfirmedAt: Flow<Long> = passphraseConfirmedAtFlow
    override suspend fun setPassphraseConfirmedAt(value: Long) { passphraseConfirmedAtFlow.value = value }
}
