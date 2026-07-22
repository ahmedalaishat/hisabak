package com.hisabak.feature.notification.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CategoryLimitAlertDao {
    @Query(
        "SELECT lastLevel FROM category_limit_alerts " +
            "WHERE categoryId = :categoryId AND periodMonth = :periodMonth",
    )
    suspend fun getLevel(categoryId: String, periodMonth: Int): Int?

    @Upsert
    suspend fun upsert(entity: CategoryLimitAlertEntity)
}
