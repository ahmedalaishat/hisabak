package com.hisabak.feature.category.domain.usecase

import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.CategoryType
import kotlinx.coroutines.flow.Flow

class ObserveCategoriesUseCase(private val repository: CategoryRepository) {
    operator fun invoke(type: CategoryType? = null, search: String? = null): Flow<List<Category>> =
        repository.observeAll(type, search)
}
