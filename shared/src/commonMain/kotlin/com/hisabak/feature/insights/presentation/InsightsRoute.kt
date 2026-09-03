package com.hisabak.feature.insights.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.insights.domain.InsightType
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun InsightsRoute(
    period: SummaryPeriod,
    onOpenCategory: (CategoryId) -> Unit,
    onOpenUncategorized: () -> Unit,
    onSetLimit: (CategoryId, Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InsightsViewModel = koinViewModel(
        key = period.name,
        parameters = { parametersOf(period, if (Locale.current.language == "ar") "ar" else "en") },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InsightsScreen(
        state = state,
        onInsightClick = { insight ->
            viewModel.onIntent(InsightsIntent.Tapped(insight))
            when {
                insight.type == InsightType.Uncategorized -> onOpenUncategorized()
                insight.category != null -> onOpenCategory(insight.category.id)
            }
        },
        onNarrativeClick = { item ->
            viewModel.onIntent(InsightsIntent.NarrativeTapped(item))
            item.category?.let { onOpenCategory(it.id) }
        },
        onSuggestionClick = { item ->
            val category = item.category ?: return@InsightsScreen
            val limit = item.suggestedLimitMinor ?: return@InsightsScreen
            viewModel.onIntent(InsightsIntent.SuggestionAccepted(item))
            onSetLimit(category.id, limit)
        },
        onIntent = viewModel::onIntent,
        modifier = modifier,
    )
}
