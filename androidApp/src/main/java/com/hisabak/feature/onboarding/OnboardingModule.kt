package com.hisabak.feature.onboarding

import com.hisabak.core.data.preferences.AppPreferencesDataStore
import com.hisabak.core.domain.AppPreferences
import com.hisabak.feature.onboarding.presentation.OnboardingViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val onboardingModule = module {
    single { AppPreferencesDataStore(androidContext()) } bind AppPreferences::class
    viewModel { OnboardingViewModel(preferences = get(), analytics = get()) }
}
