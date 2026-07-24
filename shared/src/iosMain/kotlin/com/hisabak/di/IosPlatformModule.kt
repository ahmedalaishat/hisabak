@file:OptIn(ExperimentalNativeApi::class)

package com.hisabak.di

import com.hisabak.core.common.AppConfig
import com.hisabak.core.data.backup.DataStoreBackupAccountStore
import com.hisabak.core.data.backup.DriveAuthorizer
import com.hisabak.core.data.backup.GcmCipher
import com.hisabak.core.data.backup.IosAesGcmBackupCrypto
import com.hisabak.core.data.backup.IosAutoBackupScheduler
import com.hisabak.core.data.backup.IosDriveAuthorizer
import com.hisabak.core.data.backup.IosDriveBackupRemote
import com.hisabak.core.data.backup.IosKeychainBackupPassphraseStore
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
import com.hisabak.core.platform.security.IosBiometricAuthenticator
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.platform.IosNotificationStrings
import com.hisabak.feature.notification.platform.IosNotifier
import com.hisabak.feature.sms.domain.ai.AiSmsParser
import com.hisabak.feature.sms.platform.AiSmsBridge
import com.hisabak.feature.sms.platform.IosAiSmsParser
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSBundle

/** CFBundleVersion — the iOS counterpart of Android's versionCode. */
private fun bundleBuildNumber(): Int =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String)
        ?.toIntOrNull() ?: 0

/** HisabakFlavor from Info.plist ("prod" | "staging") — the iOS counterpart of Android's flavors. */
private fun bundleFlavor(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("HisabakFlavor") as? String ?: "prod"

/**
 * iOS bindings for every platform port androidApp binds natively. Everything is real except
 * [Analytics] (no-op pending the B6 Firebase decision). [gcmCipher] is the Swift CryptoKit
 * bridge injected at startup — AES-GCM is the one primitive Kotlin/Native can't reach.
 */
fun iosPlatformModule(gcmCipher: GcmCipher, aiSmsBridge: AiSmsBridge): Module = module {
    single<AiSmsParser> { IosAiSmsParser(aiSmsBridge) }

    single {
        AppConfig(
            seedData = bundleFlavor() == "staging",
            // False in every flavor: iOS has no SMS-read API. Unlike Android's staging (which
            // carries RECEIVE_SMS), near-automatic capture on iOS is the Shortcuts action.
            smsAutoCapture = false,
            isDebug = Platform.isDebugBinary,
            versionCode = bundleBuildNumber(),
        )
    }

    single { hisabakDatabaseBuilder().build() }

    single {
        AppPreferencesDataStore(preferencesDataStore(APP_PREFS_STORE))
    } bind AppPreferences::class

    single<Analytics> { NoopAnalytics() }
    single<Notifier> { IosNotifier() }
    single<NotificationStrings> { IosNotificationStrings() }
    single<AutoBackupScheduler> { IosAutoBackupScheduler() }
    single<BiometricAvailability> { IosBiometricAuthenticator() }

    single<BackupCrypto> { IosAesGcmBackupCrypto(gcmCipher) }
    single<BackupPassphraseStore> { IosKeychainBackupPassphraseStore() }
    single<BackupAccountStore> {
        DataStoreBackupAccountStore(preferencesDataStore(BACKUP_ACCOUNT_STORE))
    }
    single<DriveAuthorizer> { IosDriveAuthorizer() }
    single<BackupRemote> { IosDriveBackupRemote(get()) }
}
