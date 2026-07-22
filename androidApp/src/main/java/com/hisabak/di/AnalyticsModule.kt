package com.hisabak.di

import com.hisabak.core.data.analytics.FirebaseAnalyticsClient
import com.hisabak.core.domain.analytics.Analytics
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val analyticsModule = module {
    single<Analytics> { FirebaseAnalyticsClient(androidContext()) }
}
