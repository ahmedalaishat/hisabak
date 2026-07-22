package com.hisabak.feature.category.domain.usecase

import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryLimitRepository
import kotlinx.coroutines.flow.Flow

class ObserveCategoryLimitsUseCase(private val repository: CategoryLimitRepository) {
    operator fun invoke(): Flow<List<CategoryLimit>> = repository.observeAll()
}
