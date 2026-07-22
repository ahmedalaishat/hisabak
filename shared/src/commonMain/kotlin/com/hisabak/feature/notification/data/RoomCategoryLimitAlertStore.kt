package com.hisabak.feature.notification.data

import com.hisabak.feature.notification.data.local.CategoryLimitAlertDao
import com.hisabak.feature.notification.data.local.CategoryLimitAlertEntity
import com.hisabak.feature.notification.domain.CategoryLimitAlertStore

class RoomCategoryLimitAlertStore(private val dao: CategoryLimitAlertDao) : CategoryLimitAlertStore {
    override suspend fun lastLevel(categoryId: String, periodMonth: Int): Int? =
        dao.getLevel(categoryId, periodMonth)

    override suspend fun record(categoryId: String, periodMonth: Int, level: Int) =
        dao.upsert(CategoryLimitAlertEntity(categoryId, periodMonth, level))
}
