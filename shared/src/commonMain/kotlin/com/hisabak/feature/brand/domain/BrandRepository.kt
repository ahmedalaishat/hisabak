package com.hisabak.feature.brand.domain

import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.core.common.DomainResult
import kotlinx.coroutines.flow.Flow

interface BrandRepository {
    fun observeAll(search: String? = null, categoryId: CategoryId? = null): Flow<List<Brand>>
    suspend fun getById(id: BrandId): DomainResult<Brand>
    suspend fun findByNameLike(name: String): Brand?

    /** Case-insensitive exact name match — unlike [findByNameLike], unambiguous. */
    suspend fun findByExactName(name: String): Brand?
    suspend fun upsert(brand: Brand): DomainResult<Unit>
    suspend fun delete(id: BrandId): DomainResult<Unit>
    suspend fun countTransactions(id: BrandId): Long

    /** Brand names ordered by transaction count (most used first), capped at [limit]. */
    suspend fun namesByUsage(limit: Int): List<String>

    /** The brand a bank's merchant string has been mapped to, or null. [alias] is case-insensitive. */
    suspend fun findByAlias(alias: String): Brand?

    /** Records that [alias] — a merchant string as a bank writes it — means [brandId]. */
    suspend fun linkAlias(alias: String, brandId: BrandId): DomainResult<Unit>
}
