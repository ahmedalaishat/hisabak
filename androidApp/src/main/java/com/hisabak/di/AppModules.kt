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

val appModules: List<Module> = listOf(
    coreModule,
    analyticsModule,
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
