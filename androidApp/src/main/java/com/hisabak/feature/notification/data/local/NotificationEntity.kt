package com.hisabak.feature.notification.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [Index("createdAtMillis"), Index("isRead")],
)
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val type: String,
    val categoryId: String?,
    val createdAtMillis: Long,
    val isRead: Boolean,
)
