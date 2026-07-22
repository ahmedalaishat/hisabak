package com.hisabak.feature.notification.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun NotificationsRoute(
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotificationsScreen(
        state = state,
        onRowClick = { row ->
            viewModel.onIntent(NotificationsIntent.MarkRead(row.id))
            row.categoryId?.let(onOpenCategory)
        },
        onDismiss = { viewModel.onIntent(NotificationsIntent.Dismiss(it.id)) },
        onMarkAllRead = { viewModel.onIntent(NotificationsIntent.MarkAllRead) },
        modifier = modifier,
    )
}
