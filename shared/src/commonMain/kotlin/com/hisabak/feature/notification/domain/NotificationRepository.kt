package com.hisabak.feature.notification.domain

import kotlinx.coroutines.flow.Flow

interface NotificationRepository {
    fun observeAll(): Flow<List<Notification>>
    fun observeUnreadCount(): Flow<Int>
    suspend fun create(notification: Notification)
    suspend fun markRead(id: NotificationId)
    suspend fun markAllRead()
    suspend fun delete(id: NotificationId)
}
