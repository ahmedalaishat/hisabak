package com.hisabak.feature.notification.presentation.list

import androidx.lifecycle.viewModelScope
import com.hisabak.core.presentation.BaseViewModel
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationRepository
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class NotificationsViewModel(
    private val repository: NotificationRepository,
) : BaseViewModel<NotificationsIntent, NotificationsUiState, NotificationsEffect>() {

    override fun initialState() = NotificationsUiState()

    init {
        repository.observeAll()
            .map { list -> list.map(::toRow) }
            .onEach { rows -> setState { copy(rows = rows, isLoading = false) } }
            .launchIn(viewModelScope)
    }

    override fun onIntent(intent: NotificationsIntent) {
        when (intent) {
            is NotificationsIntent.MarkRead ->
                viewModelScope.launch { repository.markRead(intent.id) }
            is NotificationsIntent.Dismiss ->
                viewModelScope.launch { repository.delete(intent.id) }
            NotificationsIntent.MarkAllRead ->
                viewModelScope.launch { repository.markAllRead() }
        }
    }

    private fun toRow(n: Notification) = NotificationRow(
        id = n.id,
        title = n.title,
        message = n.message,
        type = n.type,
        createdAt = n.createdAt,
        isRead = n.isRead,
        categoryId = n.categoryId,
    )
}
