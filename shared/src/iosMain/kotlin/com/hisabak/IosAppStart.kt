package com.hisabak

import com.hisabak.core.common.AppConfig
import com.hisabak.core.data.local.DatabaseSeeder
import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.di.iosPlatformModule
import com.hisabak.di.sharedModules
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

private var started = false

/** iOS counterpart of `HisabakApp.onCreate`: Koin, first-run seeding, and the budget-limit
 *  monitor. Idempotent — `MainViewController` calls it before composing the root. */
internal fun startIosApp() {
    if (started) return
    started = true
    startKoin { modules(sharedModules + iosPlatformModule) }
    val koin = KoinPlatform.getKoin()
    val appScope = koin.get<CoroutineScope>(APPLICATION_SCOPE)
    val seeder = koin.get<DatabaseSeeder>()
    val config = koin.get<AppConfig>()
    appScope.launch {
        if (config.seedData) seeder.seedIfEmpty() else seeder.seedStartersIfEmpty()
    }
    koin.get<CategoryLimitMonitor>().start(appScope)
}
