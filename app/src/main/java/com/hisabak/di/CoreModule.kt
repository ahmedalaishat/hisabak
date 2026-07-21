package com.hisabak.di

import com.hisabak.core.common.Clock
import com.hisabak.core.common.Currency
import com.hisabak.core.common.SystemClock
import com.hisabak.core.data.local.StarterData
import com.hisabak.core.platform.security.BiometricAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

val APPLICATION_SCOPE = named("applicationScope")

val coreModule = module {
    single<Clock> { SystemClock() }
    single { Currency.AED }
    single { SeedData(clock = get(), currency = get()) }
    single { StarterData() }
    // Process-lifetime scope for work that must outlive the component that started it (e.g. a
    // share/process-text capture whose translucent activity finishes before the write completes).
    single(APPLICATION_SCOPE) { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    single { BiometricAuthenticator(androidContext()) }
}
