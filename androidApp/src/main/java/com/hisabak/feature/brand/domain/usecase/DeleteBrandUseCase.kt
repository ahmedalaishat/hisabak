package com.hisabak.feature.brand.domain.usecase

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.core.common.DomainResult

class DeleteBrandUseCase(private val repository: BrandRepository) {
    suspend operator fun invoke(id: BrandId): DomainResult<Unit> = repository.delete(id)
}
