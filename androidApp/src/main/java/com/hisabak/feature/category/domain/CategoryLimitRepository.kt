package com.hisabak.feature.category.domain

import com.hisabak.core.common.Money
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

interface CategoryLimitRepository {
    fun observeAll(): Flow<List<CategoryLimit>>

    /** Upserts the limit for [categoryId] effective from [effectiveFrom]. A `null` [amount]
     *  records a "no limit from here" marker, leaving earlier months untouched. */
    suspend fun setLimit(categoryId: CategoryId, amount: Money?, effectiveFrom: YearMonth)
}
