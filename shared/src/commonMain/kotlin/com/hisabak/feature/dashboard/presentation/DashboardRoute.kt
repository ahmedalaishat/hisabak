package com.hisabak.feature.dashboard.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hisabak.core.common.SummaryPeriod
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardRoute(
    onShowUncategorized: () -> Unit,
    onOpenInsights: (SummaryPeriod) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = koinViewModel(),
    categoryFocusBus: CategoryFocusBus = koinInject(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusCategoryId by categoryFocusBus.pending.collectAsStateWithLifecycle()
    DashboardScreen(
        state = state,
        onPeriodChange = { viewModel.onIntent(DashboardIntent.PeriodChanged(it)) },
        onShowUncategorized = onShowUncategorized,
        onOpenInsights = onOpenInsights,
        focusCategoryId = focusCategoryId,
        onFocusConsumed = { categoryFocusBus.consume() },
        modifier = modifier,
    )
}
