package com.hisabak.feature.transaction.presentation.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.feature.transaction.domain.TransactionId
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TransactionListRoute(
    onAdd: () -> Unit,
    onEdit: (TransactionId) -> Unit,
    viewModel: TransactionListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TransactionListScreen(
        state = state,
        onSearchChange = { viewModel.onIntent(TransactionListIntent.SearchChanged(it)) },
        onPeriodChange = { viewModel.onIntent(TransactionListIntent.PeriodChanged(it)) },
        onBrandFilterChange = { viewModel.onIntent(TransactionListIntent.BrandFilterChanged(it)) },
        onCategoryFilterChange = { viewModel.onIntent(TransactionListIntent.CategoryFilterChanged(it)) },
        onDateRangeChange = { viewModel.onIntent(TransactionListIntent.DateRangeChanged(it)) },
        onClearFilters = { viewModel.onIntent(TransactionListIntent.ClearFilters) },
        onDelete = { viewModel.onIntent(TransactionListIntent.Delete(it)) },
        onAdd = onAdd,
        onEdit = onEdit,
    )
}
