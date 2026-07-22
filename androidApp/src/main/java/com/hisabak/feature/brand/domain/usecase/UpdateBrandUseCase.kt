package com.hisabak.feature.brand.domain.usecase

import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.core.common.DomainResult

class UpdateBrandUseCase(
    private val repository: BrandRepository,
) {
    suspend operator fun invoke(brand: Brand): DomainResult<Unit> =
        repository.upsert(brand)
}
