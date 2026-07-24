package com.hisabak

import com.hisabak.core.common.AppConfig
import com.hisabak.core.data.backup.GcmCipher
import com.hisabak.core.data.backup.registerAutoBackupTask
import com.hisabak.core.data.local.DatabaseSeeder
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.di.iosPlatformModule
import com.hisabak.di.sharedModules
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.platform.installNotificationTapHandler
import com.hisabak.feature.sms.platform.AiSmsBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

private var started = false

/**
 * iOS counterpart of `HisabakApp.onCreate`: Koin, first-run seeding, the budget-limit monitor,
 * and the auto-backup task registration + reschedule. Called from `iOSApp.swift`'s init —
 * BGTaskScheduler registration must complete before the app finishes launching. [gcmCipher] is
 * the Swift CryptoKit bridge (see `CryptoKitGcmCipher.swift`); [aiSmsBridge] the Swift
 * Foundation Models bridge (see `FoundationModelsSmsParser.swift`).
 */
fun startIosApp(gcmCipher: GcmCipher, aiSmsBridge: AiSmsBridge) {
    if (started) return
    started = true
    startKoin { modules(sharedModules + iosPlatformModule(gcmCipher, aiSmsBridge)) }
    val koin = KoinPlatform.getKoin()
    val appScope = koin.get<CoroutineScope>(APPLICATION_SCOPE)
    registerAutoBackupTask(appScope)
    installNotificationTapHandler()
    val seeder = koin.get<DatabaseSeeder>()
    val config = koin.get<AppConfig>()
    appScope.launch {
        if (config.seedData) seeder.seedIfEmpty() else seeder.seedStartersIfEmpty()
    }
    koin.get<CategoryLimitMonitor>().start(appScope)
    // Reconcile the auto-backup schedule with the current settings on each launch.
    appScope.launch {
        val preferences = koin.get<AppPreferences>()
        koin.get<AutoBackupScheduler>().schedule(
            preferences.autoBackupPeriod.first(),
            preferences.backupEnabled.first(),
        )
    }
}

internal fun requireIosAppStarted() {
    check(started) { "startIosApp(gcmCipher) must be called from iOSApp.init before creating the view controller." }
}
