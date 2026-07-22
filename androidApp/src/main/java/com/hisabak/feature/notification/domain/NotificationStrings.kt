package com.hisabak.feature.notification.domain

/**
 * Localized copy for notifications. The text is built where the [Notification] is created (so the
 * stored record and the system notification match), which is why this lives in domain and is
 * implemented in the platform layer against the app's chosen language.
 */
interface NotificationStrings {
    fun transactionRecordedTitle(): String
    fun transactionRecorded(amount: String, brand: String, category: String): String
    fun transactionRecordedUncategorized(amount: String, brand: String): String
    fun budgetReachedTitle(category: String): String
    fun budgetLevelTitle(category: String, level: Int): String
    fun budgetMessage(spent: String, limit: String): String
}
