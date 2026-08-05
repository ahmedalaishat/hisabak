package com.hisabak.feature.settings

import com.hisabak.feature.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        SettingsViewModel(
            preferences = get(),
            passphraseStore = get(),
            clock = get(),
            analytics = get(),
        )
    }
}
