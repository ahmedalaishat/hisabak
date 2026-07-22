package com.hisabak.di

import com.hisabak.BuildConfig
import com.hisabak.core.common.AppConfig
import com.hisabak.core.data.backup.AesGcmBackupCrypto
import com.hisabak.core.data.backup.DataStoreBackupAccountStore
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.data.backup.GoogleDriveAuthorizer
import com.hisabak.core.data.backup.GoogleDriveBackupRemote
import com.hisabak.core.data.backup.KeystoreBackupPassphraseStore
import com.hisabak.core.data.backup.WorkManagerAutoBackupScheduler
import com.hisabak.core.data.local.hisabakDatabaseBuilder
import com.hisabak.core.data.local.security.DatabaseDecryptionMigration
import com.hisabak.core.data.local.security.KeystoreDatabaseKeyStore
import com.hisabak.core.data.preferences.APP_PREFS_STORE
import com.hisabak.core.data.preferences.AppPreferencesDataStore
import com.hisabak.core.data.preferences.BACKUP_ACCOUNT_STORE
import com.hisabak.core.data.preferences.preferencesDataStore
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.core.domain.backup.BackupAccountStore
import com.hisabak.core.domain.backup.BackupCrypto
import com.hisabak.core.domain.backup.BackupPassphraseStore
import com.hisabak.core.domain.backup.BackupRemote
import com.hisabak.core.domain.security.BiometricAvailability
import com.hisabak.core.platform.security.BiometricAuthenticator
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.platform.AndroidNotificationStrings
import com.hisabak.feature.notification.platform.SystemNotifier
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module

/** Android bindings for every platform port the shared modules depend on. */
val platformModule = module {
    single {
        AppConfig(
            seedData = BuildConfig.SEED_DATA,
            smsAutoCapture = BuildConfig.SMS_AUTO_CAPTURE,
            isDebug = BuildConfig.DEBUG,
            versionCode = BuildConfig.VERSION_CODE,
        )
    }

    single {
        // Versions up to 1.8.x encrypted this file with SQLCipher; databases carried over from
        // them are decrypted in place (once) before Room opens.
        DatabaseDecryptionMigration.migrateIfEncrypted(androidContext(), KeystoreDatabaseKeyStore(androidContext()))
        hisabakDatabaseBuilder(
            context = androidContext(),
            useDestructiveFallback = BuildConfig.DEBUG,
        ).build()
    }

    single {
        AppPreferencesDataStore(preferencesDataStore(androidContext(), APP_PREFS_STORE))
    } bind AppPreferences::class

    single { BiometricAuthenticator(androidContext()) } bind BiometricAvailability::class

    single { SystemNotifier(androidContext()) } bind Notifier::class
    single<NotificationStrings> { AndroidNotificationStrings(androidContext()) }

    single<BackupPassphraseStore> { KeystoreBackupPassphraseStore(androidContext()) }
    single<BackupAccountStore> {
        DataStoreBackupAccountStore(preferencesDataStore(androidContext(), BACKUP_ACCOUNT_STORE))
    }
    single<DriveAuthorizer> { GoogleDriveAuthorizer(androidContext()) }
    single<BackupRemote> { GoogleDriveBackupRemote(authorizer = get()) }
    single<AutoBackupScheduler> { WorkManagerAutoBackupScheduler(androidContext()) }
    single<BackupCrypto> { AesGcmBackupCrypto() }
}
