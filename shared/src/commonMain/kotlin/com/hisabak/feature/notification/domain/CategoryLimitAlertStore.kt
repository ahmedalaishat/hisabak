package com.hisabak.feature.notification.domain

/**
 * Tracks the highest limit-alert level already fired per category per month so each threshold
 * fires at most once. Domain-level port; Android backs it with a Room DAO.
 */
interface CategoryLimitAlertStore {
    suspend fun lastLevel(categoryId: String, periodMonth: Int): Int?
    suspend fun record(categoryId: String, periodMonth: Int, level: Int)
}
