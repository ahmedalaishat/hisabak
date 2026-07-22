package com.hisabak.feature.category.data

import com.hisabak.core.common.Currency
import com.hisabak.core.common.Money
import com.hisabak.feature.category.data.local.CategoryLimitDao
import com.hisabak.feature.category.data.local.CategoryLimitEntity
import com.hisabak.feature.category.data.local.toDomain
import com.hisabak.feature.category.data.local.toEncoded
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryLimit
import com.hisabak.feature.category.domain.CategoryLimitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth

class RoomCategoryLimitRepository(
    private val dao: CategoryLimitDao,
    private val currency: Currency,
) : CategoryLimitRepository {

    override fun observeAll(): Flow<List<CategoryLimit>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setLimit(categoryId: CategoryId, amount: Money?, effectiveFrom: YearMonth) {
        dao.upsert(
            CategoryLimitEntity(
                categoryId = categoryId.value,
                effectiveFrom = effectiveFrom.toEncoded(),
                amountMinor = amount?.amountMinor,
                currency = (amount?.currency ?: currency).code,
            ),
        )
    }
}
