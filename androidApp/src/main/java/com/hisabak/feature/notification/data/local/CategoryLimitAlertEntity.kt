package com.hisabak.feature.notification.data.local

import androidx.room.Entity

/**
 * Remembers the highest limit threshold (0/50/80/100) already alerted for a category in a given
 * month, so each threshold notifies at most once and we don't re-alert when spend dips and rises.
 * [periodMonth] is encoded as `year * 100 + month` (e.g. 202606), matching CategoryLimitEntity.
 */
@Entity(tableName = "category_limit_alerts", primaryKeys = ["categoryId", "periodMonth"])
data class CategoryLimitAlertEntity(
    val categoryId: String,
    val periodMonth: Int,
    val lastLevel: Int,
)
