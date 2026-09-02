package com.hisabak.feature.category.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.feature.category.domain.CategoryId
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CategoryListRoute(
    onAdd: () -> Unit,
    onEdit: (CategoryId) -> Unit,
    onViewTransactions: (CategoryId) -> Unit,
    showHeader: Boolean = true,
    viewModel: CategoryListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CategoryListScreen(
        state = state,
        onSearchChange = { viewModel.onIntent(CategoryListIntent.SearchChanged(it)) },
        onTypeFilterChange = { viewModel.onIntent(CategoryListIntent.TypeFilterChanged(it)) },
        onAdd = onAdd,
        onEdit = onEdit,
        onViewTransactions = onViewTransactions,
        showHeader = showHeader,
    )
}
