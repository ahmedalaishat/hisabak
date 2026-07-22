package com.hisabak.feature.onboarding

import com.hisabak.core.data.preferences.APP_PREFS_STORE
import com.hisabak.core.data.preferences.AppPreferencesDataStore
import com.hisabak.core.data.preferences.preferencesDataStore
import com.hisabak.core.domain.AppPreferences
import com.hisabak.feature.onboarding.presentation.OnboardingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val onboardingModule = module {
    single {
        AppPreferencesDataStore(preferencesDataStore(androidContext(), APP_PREFS_STORE))
    } bind AppPreferences::class
    viewModel { OnboardingViewModel(preferences = get(), analytics = get()) }
}
