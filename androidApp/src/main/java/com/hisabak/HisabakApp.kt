package com.hisabak

import android.app.Application
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hisabak.core.data.local.DatabaseSeeder
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.backup.AutoBackupScheduler
import com.hisabak.di.APPLICATION_SCOPE
import com.hisabak.di.appModules
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.platform.SystemNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class HisabakApp : Application() {

    private val seeder: DatabaseSeeder by inject()
    private val systemNotifier: SystemNotifier by inject()
    private val limitMonitor: CategoryLimitMonitor by inject()
    private val appScope: CoroutineScope by inject(APPLICATION_SCOPE)
    private val appPreferences: AppPreferences by inject()
    private val autoBackupScheduler: AutoBackupScheduler by inject()

    override fun onCreate() {
        super.onCreate()
        // Crash reporting and analytics are on for release builds and off for debug, so local
        // development never pollutes the Firebase dashboards.
        FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
        FirebaseAnalytics.getInstance(this).setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@HisabakApp)
            modules(appModules)
        }
        systemNotifier.ensureChannel()
        // Staging/dev gets the full demo dataset; production first-run gets just the starter
        // categories so the app is immediately usable without shipping demo data.
        appScope.launch {
            if (BuildConfig.SEED_DATA) seeder.seedIfEmpty() else seeder.seedStartersIfEmpty()
        }
        limitMonitor.start(appScope)
        // Reconcile the auto-backup schedule with the current settings on each launch.
        appScope.launch {
            autoBackupScheduler.schedule(
                appPreferences.autoBackupPeriod.first(),
                appPreferences.backupEnabled.first(),
            )
        }
    }
}
