package com.hisabak.feature.notification.presentation.list

import com.hisabak.core.presentation.ViewEffect
import com.hisabak.core.presentation.ViewIntent
import com.hisabak.core.presentation.ViewState
import com.hisabak.feature.notification.domain.NotificationId
import java.time.Instant

data class NotificationRow(
    val id: NotificationId,
    val title: String,
    val message: String,
    val type: String,
    val createdAt: Instant,
    val isRead: Boolean,
    val categoryId: String?,
)

data class NotificationsUiState(
    val rows: List<NotificationRow> = emptyList(),
    val isLoading: Boolean = true,
) : ViewState {
    val hasUnread: Boolean get() = rows.any { !it.isRead }
}

sealed interface NotificationsIntent : ViewIntent {
    data class MarkRead(val id: NotificationId) : NotificationsIntent
    data class Dismiss(val id: NotificationId) : NotificationsIntent
    data object MarkAllRead : NotificationsIntent
}

sealed interface NotificationsEffect : ViewEffect
