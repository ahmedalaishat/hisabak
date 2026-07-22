package com.hisabak.di

import com.hisabak.ManageViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val manageModule = module {
    viewModel { ManageViewModel(observeBrands = get(), observeCategories = get()) }
}
