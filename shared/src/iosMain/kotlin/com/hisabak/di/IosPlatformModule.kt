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
import com.hisabak.core.domain.backup.backupFileName
import com.hisabak.core.domain.security.BiometricAvailability
import com.hisabak.core.platform.NoopAnalytics
import com.hisabak.core.platform.security.IosBiometricAuthenticator
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.platform.IosNotificationStrings
import com.hisabak.feature.notification.platform.IosNotifier
import com.hisabak.feature.brand.domain.ai.AiCategorySuggester
import com.hisabak.feature.brand.platform.AiCategoryBridge
import com.hisabak.feature.brand.platform.IosAiCategorySuggester
import com.hisabak.feature.sms.domain.ai.AiSmsParser
import com.hisabak.core.domain.remote.ServiceConfig
import com.hisabak.core.domain.remote.ServiceTransport
import com.hisabak.core.platform.remote.IosServiceTransport
import com.hisabak.feature.sms.domain.ai.RemoteAiSmsParser
import com.hisabak.feature.sms.domain.ai.PreferredAiSmsParser
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

/** Parse-service settings from Info.plist; absent or blank disables the remote parser. */
private fun bundleString(key: String): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String ?: ""

/** HisabakFlavor from Info.plist ("prod" | "staging") — the iOS counterpart of Android's flavors. */
private fun bundleFlavor(): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("HisabakFlavor") as? String ?: "prod"

/**
 * iOS bindings for every platform port androidApp binds natively. Everything is real except
 * [Analytics] (no-op pending the B6 Firebase decision). [gcmCipher] is the Swift CryptoKit
 * bridge injected at startup — AES-GCM is the one primitive Kotlin/Native can't reach.
 */
fun iosPlatformModule(
    gcmCipher: GcmCipher,
    aiSmsBridge: AiSmsBridge,
    aiCategoryBridge: AiCategoryBridge,
): Module = module {
    single<ServiceTransport> {
        val config: AppConfig = get()
        IosServiceTransport(ServiceConfig(config.parseServiceUrl, config.parseServiceToken))
    }

    single<AiSmsParser> {
        PreferredAiSmsParser(
            onDevice = IosAiSmsParser(aiSmsBridge),
            remote = RemoteAiSmsParser(client = get(), preferences = get()),
        )
    }
    single<AiCategorySuggester> { IosAiCategorySuggester(aiCategoryBridge) }

    single {
        AppConfig(
            flavor = bundleFlavor(),
            seedData = bundleFlavor() == "staging",
            // False in every flavor: iOS has no SMS-read API. Unlike Android's staging (which
            // carries RECEIVE_SMS), near-automatic capture on iOS is the Shortcuts action.
            smsAutoCapture = false,
            isDebug = Platform.isDebugBinary,
            versionCode = bundleBuildNumber(),
            parseServiceUrl = bundleString("HisabakParseServiceURL"),
            parseServiceToken = bundleString("HisabakParseServiceToken"),
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
    single<BackupRemote> {
        IosDriveBackupRemote(get(), fileName = backupFileName(get<AppConfig>().flavor))
    }
}
