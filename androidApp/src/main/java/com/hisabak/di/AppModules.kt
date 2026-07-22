package com.hisabak.di

import org.koin.core.module.Module

/** Everything `startKoin` needs on Android: the shared (pure) modules plus the platform bindings. */
val appModules: List<Module> = sharedModules + listOf(
    platformModule,
    analyticsModule,
)
