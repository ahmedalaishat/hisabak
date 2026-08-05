package com.hisabak.feature.category.domain

import com.hisabak.core.common.DomainResult
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeAll(type: CategoryType? = null, search: String? = null): Flow<List<Category>>
    suspend fun getById(id: CategoryId): DomainResult<Category>
    suspend fun upsert(category: Category): DomainResult<Unit>
    suspend fun delete(id: CategoryId): DomainResult<Unit>
}
