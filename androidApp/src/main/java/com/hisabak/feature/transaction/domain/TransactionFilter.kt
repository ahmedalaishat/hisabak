package com.hisabak.feature.transaction.domain

import com.hisabak.feature.brand.domain.BrandId
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.category.domain.CategoryType
import java.time.Instant

data class TransactionFilter(
    val search: String? = null,
    val brandId: BrandId? = null,
    val categoryId: CategoryId? = null,
    val categoryType: CategoryType? = null,
    val dateFrom: Instant? = null,
    val dateTo: Instant? = null,
) {
    companion object {
        val NONE = TransactionFilter()
    }
}
