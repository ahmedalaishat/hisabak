package com.hisabak.feature.category.domain.usecase

import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.core.common.DomainResult

class DeleteCategoryUseCase(private val repository: CategoryRepository) {
    suspend operator fun invoke(id: CategoryId): DomainResult<Unit> = repository.delete(id)
}
