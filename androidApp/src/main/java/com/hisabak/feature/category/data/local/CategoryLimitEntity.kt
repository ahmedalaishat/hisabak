package com.hisabak.feature.category.data.local

import androidx.room.Entity

/**
 * A category's monthly limit, keyed by category + the month it takes effect.
 * [effectiveFrom] is encoded as `year * 100 + month` (e.g. 202606) for cheap ordering.
 * [amountMinor] is null when the limit was cleared from that month forward.
 */
@Entity(tableName = "category_limits", primaryKeys = ["categoryId", "effectiveFrom"])
data class CategoryLimitEntity(
    val categoryId: String,
    val effectiveFrom: Int,
    val amountMinor: Long?,
    val currency: String,
)
