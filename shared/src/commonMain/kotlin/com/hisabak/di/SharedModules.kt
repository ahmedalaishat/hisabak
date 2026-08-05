package com.hisabak.di

import com.hisabak.feature.brand.brandModule
import com.hisabak.feature.category.categoryModule
import com.hisabak.feature.dashboard.dashboardModule
import com.hisabak.feature.notification.notificationModule
import com.hisabak.feature.onboarding.onboardingModule
import com.hisabak.feature.settings.settingsModule
import com.hisabak.feature.sms.smsModule
import com.hisabak.feature.transaction.transactionModule
import org.koin.core.module.Module

/**
 * Every platform-agnostic Koin module: use cases, repositories, ViewModels, and the DAO wiring.
 * Each app adds its platform module on top — androidApp's `appModules`, iOS's `iosPlatformModule` —
 * which must bind: `AppConfig`, `HisabakDatabase`, `AppPreferences`, `Analytics`, `Notifier`,
 * `NotificationStrings`, `BiometricAvailability`, and the backup platform ports
 * (`BackupCrypto`, `BackupPassphraseStore`, `BackupAccountStore`, `DriveAuthorizer`,
 * `BackupRemote`, `AutoBackupScheduler`).
 */
val sharedModules: List<Module> = listOf(
    coreModule,
    databaseModule,
    categoryModule,
    brandModule,
    transactionModule,
    dashboardModule,
    smsModule,
    notificationModule,
    manageModule,
    onboardingModule,
    settingsModule,
    backupModule,
)
