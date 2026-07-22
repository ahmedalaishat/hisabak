package com.hisabak.feature.category.data

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.category.data.local.CategoryDao
import com.hisabak.feature.category.data.local.toDomain
import com.hisabak.feature.category.data.local.toEntity
import com.hisabak.feature.category.domain.Category
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryRepository
import com.hisabak.feature.category.domain.CategoryType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val dao: CategoryDao,
) : CategoryRepository {

    override fun observeAll(type: CategoryType?, search: String?): Flow<List<Category>> =
        dao.observeAll().map { rows ->
            rows.asSequence()
                .map { it.toDomain() }
                .filter { c -> type?.let { c.type == it } ?: true }
                .filter { c -> search?.let { q -> c.name.contains(q, ignoreCase = true) } ?: true }
                .toList()
        }

    override suspend fun getById(id: CategoryId): DomainResult<Category> =
        dao.getById(id.value)
            ?.toDomain()
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Category", id.value))

    override suspend fun upsert(category: Category): DomainResult<Unit> {
        dao.upsert(category.toEntity())
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: CategoryId): DomainResult<Unit> {
        dao.deleteById(id.value)
        return DomainResult.Success(Unit)
    }
}
