package com.hisabak.feature.transaction.data

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.transaction.data.local.TransactionDao
import com.hisabak.feature.transaction.data.local.toDomain
import com.hisabak.feature.transaction.data.local.toEntity
import com.hisabak.feature.transaction.domain.PagedTransactions
import com.hisabak.feature.transaction.domain.Transaction
import com.hisabak.feature.transaction.domain.TransactionFilter
import com.hisabak.feature.transaction.domain.TransactionId
import com.hisabak.feature.transaction.domain.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTransactionRepository(
    private val dao: TransactionDao,
) : TransactionRepository {

    override fun observe(filter: TransactionFilter): Flow<List<Transaction>> =
        dao.observeFiltered(
            search = filter.search?.takeIf(String::isNotBlank),
            brandId = filter.brandId?.value,
            dateFromMillis = filter.dateFrom?.toEpochMilliseconds(),
            dateToMillis = filter.dateTo?.toEpochMilliseconds(),
        ).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getPage(
        filter: TransactionFilter,
        page: Int,
        perPage: Int,
    ): DomainResult<PagedTransactions> {
        val search = filter.search?.takeIf(String::isNotBlank)
        val brandId = filter.brandId?.value
        val from = filter.dateFrom?.toEpochMilliseconds()
        val to = filter.dateTo?.toEpochMilliseconds()
        val offset = ((page - 1) * perPage).coerceAtLeast(0)
        val rows = dao.pageFiltered(search, brandId, from, to, perPage, offset)
        val total = dao.countFiltered(search, brandId, from, to)
        return DomainResult.Success(
            PagedTransactions(
                items = rows.map { it.toDomain() },
                page = page,
                perPage = perPage,
                total = total,
            ),
        )
    }

    override suspend fun getById(id: TransactionId): DomainResult<Transaction> =
        dao.getById(id.value)
            ?.toDomain()
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("Transaction", id.value))

    override suspend fun upsert(transaction: Transaction): DomainResult<Unit> {
        dao.upsert(transaction.toEntity())
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: TransactionId): DomainResult<Unit> {
        dao.deleteById(id.value)
        return DomainResult.Success(Unit)
    }

    override suspend fun reassignBrand(fromBrandId: BrandId, toBrandId: BrandId): DomainResult<Unit> {
        dao.reassignBrand(fromBrandId.value, toBrandId.value)
        return DomainResult.Success(Unit)
    }
}
