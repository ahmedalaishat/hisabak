package com.hisabak.feature.brand.domain

import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.core.common.DomainResult
import kotlinx.coroutines.flow.Flow

interface BrandRepository {
    fun observeAll(search: String? = null, categoryId: CategoryId? = null): Flow<List<Brand>>
    suspend fun getById(id: BrandId): DomainResult<Brand>
    suspend fun findByNameLike(name: String): Brand?
    suspend fun upsert(brand: Brand): DomainResult<Unit>
    suspend fun delete(id: BrandId): DomainResult<Unit>
    suspend fun countTransactions(id: BrandId): Long
}
