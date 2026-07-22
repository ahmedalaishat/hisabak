package com.hisabak.feature.notification.data

import com.hisabak.feature.notification.data.local.NotificationDao
import com.hisabak.feature.notification.data.local.toDomain
import com.hisabak.feature.notification.data.local.toEntity
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationId
import com.hisabak.feature.notification.domain.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNotificationRepository(
    private val dao: NotificationDao,
) : NotificationRepository {

    override fun observeAll(): Flow<List<Notification>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    override suspend fun create(notification: Notification) = dao.upsert(notification.toEntity())

    override suspend fun markRead(id: NotificationId) = dao.markRead(id.value)

    override suspend fun markAllRead() = dao.markAllRead()

    override suspend fun delete(id: NotificationId) = dao.deleteById(id.value)
}
