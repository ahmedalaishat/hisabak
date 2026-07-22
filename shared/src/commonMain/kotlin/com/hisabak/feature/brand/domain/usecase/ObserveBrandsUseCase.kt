package com.hisabak.feature.brand.domain.usecase

import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.category.domain.CategoryId
import kotlinx.coroutines.flow.Flow

class ObserveBrandsUseCase(private val repository: BrandRepository) {
    operator fun invoke(search: String? = null, categoryId: CategoryId? = null): Flow<List<Brand>> =
        repository.observeAll(search, categoryId)
}
