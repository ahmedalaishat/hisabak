package com.hisabak.feature.category.domain.usecase

import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.core.common.DomainResult

class UpdateCategoryUseCase(
    private val repository: CategoryRepository,
) {
    suspend operator fun invoke(category: Category): DomainResult<Unit> =
        repository.upsert(category)
}
