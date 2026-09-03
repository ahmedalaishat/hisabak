package com.hisabak.feature.brand.domain.usecase

import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.core.common.DomainResult

/**
 * Mirrors Hisabi's Brand::findOrCreateNew — resolves an existing brand first, creates if missing.
 *
 * Only ever called with a machine-extracted merchant string (the SMS/template commit path), which
 * is why it may lean on [ResolveBrandUseCase]'s fuzzier rungs. A user-typed brand name goes
 * through the brand editor instead, and must not be silently merged into a near neighbour.
 */
class FindOrCreateBrandUseCase(
    private val repository: BrandRepository,
    private val resolve: ResolveBrandUseCase,
) {
    suspend operator fun invoke(name: String): DomainResult<Brand> {
        val normalized = name.trim()
        if (normalized.isEmpty()) return DomainResult.Failure(
            com.hisabak.core.common.DomainError.ValidationFailed("Brand name required")
        )
        resolve(normalized)?.let { return DomainResult.Success(it) }
        val brand = Brand(
            id = BrandId.new(),
            name = normalized,
            categoryId = null,
        )
        return repository.upsert(brand).map { brand }
    }
}
