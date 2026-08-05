package com.hisabak

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabak.feature.brand.domain.usecase.ObserveBrandsUseCase
import com.hisabak.feature.category.domain.usecase.ObserveCategoriesUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ManageCounts(val brands: Int = 0, val categories: Int = 0)

/** Live brand/category counts for the Manage screen's switcher cards. */
class ManageViewModel(
    observeBrands: ObserveBrandsUseCase,
    observeCategories: ObserveCategoriesUseCase,
) : ViewModel() {
    val counts: StateFlow<ManageCounts> =
        combine(observeBrands(), observeCategories()) { brands, categories ->
            ManageCounts(brands = brands.size, categories = categories.size)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ManageCounts())
}
