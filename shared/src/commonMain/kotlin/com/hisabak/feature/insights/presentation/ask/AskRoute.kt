package com.hisabak.feature.insights.presentation.ask

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hisabak.core.common.SummaryPeriod
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AskRoute(
    period: SummaryPeriod,
    initialQuestion: String?,
    modifier: Modifier = Modifier,
    viewModel: AskViewModel = koinViewModel(
        key = "${period.name}:${initialQuestion.orEmpty()}",
        parameters = {
            parametersOf(period, if (Locale.current.language == "ar") "ar" else "en", initialQuestion)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AskScreen(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
