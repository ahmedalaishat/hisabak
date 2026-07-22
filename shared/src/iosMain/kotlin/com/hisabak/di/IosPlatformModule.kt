package com.hisabak.di

import com.hisabak.core.common.AppConfig
import com.hisabak.core.data.backup.DataStoreBackupAccountStore
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.data.local.hisabakDatabaseBuilder
import com.hisabak.core.data.preferences.APP_PREFS_STORE
import com.hisabak.core.data.preferences.AppPreferencesDataStore
import com.hisabak.core.data.preferences.BACKUP_ACCOUNT_STORE
import com.hisabak.core.data.preferences.preferencesDataStore
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.BackupAccountStore
import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.security.BiometricAvailability
import com.hisabak.core.platform.NoopAnalytics
import com.hisabak.core.platform.NoopAutoBackupScheduler
import com.hisabak.core.platform.NoopNotificationStrings
import com.hisabak.core.platform.NoopNotifier
import com.hisabak.core.platform.UnavailableBiometricAvailability
import com.hisabak.core.platform.UnavailableDriveAuthorizer
import com.hisabak.core.platform.UnsupportedBackupCrypto
import com.hisabak.core.platform.UnsupportedBackupPassphraseStore
import com.hisabak.core.platform.UnsupportedBackupRemote
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * iOS bindings for every platform port androidApp binds natively. Database + DataStore are real
 * (bundled SQLite driver / Documents dir); the rest are Phase A stubs — see `core/platform/IosStubs`.
 * TODO(Phase-B): replace the stubs with real implementations.
 */
val iosPlatformModule: Module = module {
    single {
        AppConfig(
            seedData = false,
            smsAutoCapture = false,
            isDebug = false,
            // TODO(Phase-B): read the real build number from the iOS bundle.
            versionCode = 0,
        )
    }

    single { hisabakDatabaseBuilder().build() }

    single {
        AppPreferencesDataStore(preferencesDataStore(APP_PREFS_STORE))
    } bind AppPreferences::class

    single<Analytics> { NoopAnalytics() }
    single<Notifier> { NoopNotifier() }
    single<NotificationStrings> { NoopNotificationStrings() }
    single<AutoBackupScheduler> { NoopAutoBackupScheduler() }
    single<BiometricAvailability> { UnavailableBiometricAvailability() }

    single<BackupCrypto> { UnsupportedBackupCrypto() }
    single<BackupPassphraseStore> { UnsupportedBackupPassphraseStore() }
    single<BackupAccountStore> {
        DataStoreBackupAccountStore(preferencesDataStore(BACKUP_ACCOUNT_STORE))
    }
    single<DriveAuthorizer> { UnavailableDriveAuthorizer() }
    single<BackupRemote> { UnsupportedBackupRemote() }
}
