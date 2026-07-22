package com.hisabak.feature.notification.data.local

import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationId
import java.time.Instant

fun NotificationEntity.toDomain(): Notification = Notification(
    id = NotificationId(id),
    title = title,
    message = message,
    type = type,
    categoryId = categoryId,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    isRead = isRead,
)

fun Notification.toEntity(): NotificationEntity = NotificationEntity(
    id = id.value,
    title = title,
    message = message,
    type = type,
    categoryId = categoryId,
    createdAtMillis = createdAt.toEpochMilli(),
    isRead = isRead,
)
