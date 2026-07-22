package com.hisabak.feature.notification.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class NotificationId(val value: String) {
    companion object {
        fun new(): NotificationId = NotificationId(UUID.randomUUID().toString())
    }
}

/** An in-app notification record. [categoryId] is the optional deep-link payload — when set,
 *  tapping the notification opens the dashboard with that category focused. */
data class Notification(
    val id: NotificationId,
    val title: String,
    val message: String,
    val type: String,
    val categoryId: String?,
    val createdAt: Instant,
    val isRead: Boolean,
) {
    companion object {
        const val TYPE_CATEGORY_LIMIT = "category_limit"
        const val TYPE_TRANSACTION_RECORDED = "transaction_recorded"
    }
}
