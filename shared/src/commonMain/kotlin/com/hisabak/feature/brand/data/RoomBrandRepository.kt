package com.hisabak.feature.brand.data

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.data.local.BrandDao
import com.hisabak.feature.brand.data.local.toDomain
import com.hisabak.feature.brand.data.local.toEntity
import com.hisabak.feature.brand.domain.Brand
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.transaction.data.local.TransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomBrandRepository(
    private val dao: BrandDao,
    private val transactionDao: TransactionDao,
) : BrandRepository {

    override fun observeAll(search: String?, categoryId: CategoryId?): Flow<List<Brand>> =
        dao.observeFiltered(search?.takeIf(String::isNotBlank), categoryId?.value)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: BrandId): DomainResult<Brand> =
        dao.getById(id.value)
            ?.toDomain()
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Brand", id.value))

    override suspend fun findByNameLike(name: String): Brand? {
        val q = name.trim()
        if (q.isEmpty()) return null
        return dao.findByNameLike(q)?.toDomain()
    }

    override suspend fun upsert(brand: Brand): DomainResult<Unit> {
        dao.upsert(brand.toEntity())
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: BrandId): DomainResult<Unit> = try {
        // Transactions reference brands with ON DELETE RESTRICT, so deleting a brand that still
        // has transactions throws. Surface it as a failure instead of crashing.
        dao.deleteById(id.value)
        DomainResult.Success(Unit)
    } catch (e: Exception) {
        DomainResult.Failure(DomainError.Unexpected(e))
    }

    override suspend fun countTransactions(id: BrandId): Long =
        transactionDao.countForBrand(id.value)
}
