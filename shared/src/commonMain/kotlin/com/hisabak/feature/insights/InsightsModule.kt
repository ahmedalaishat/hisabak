package com.hisabak.feature.insights

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.insights.presentation.InsightsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val insightsModule = module {
    viewModel { (period: SummaryPeriod) ->
        InsightsViewModel(getMetrics = get(), analytics = get(), period = period)
    }
}
