package com.hisabak.feature.onboarding

import com.hisabak.feature.onboarding.presentation.OnboardingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** The `AppPreferences` binding is per platform (DataStore path needs the platform's files dir). */
val onboardingModule = module {
    viewModel { OnboardingViewModel(preferences = get(), analytics = get()) }
}
