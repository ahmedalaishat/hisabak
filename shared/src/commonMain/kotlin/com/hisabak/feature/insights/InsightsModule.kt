package com.hisabak.feature.insights

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.insights.data.RoomNarrativeCache
import com.hisabak.feature.insights.domain.ai.AiInsights
import com.hisabak.feature.insights.domain.ai.GenerateNarrativeUseCase
import com.hisabak.feature.insights.domain.ai.NarrativeCache
import com.hisabak.feature.insights.domain.ai.RemoteAiInsights
import com.hisabak.feature.insights.domain.ai.RemoteInsightsClient
import com.hisabak.feature.insights.domain.ai.ServiceRemoteInsightsClient
import com.hisabak.feature.insights.presentation.InsightsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val insightsModule = module {
    single<RemoteInsightsClient> { ServiceRemoteInsightsClient(transport = get()) }
    single<AiInsights> { RemoteAiInsights(client = get(), currency = get()) }
    single<NarrativeCache> { RoomNarrativeCache(dao = get()) }
    factory { GenerateNarrativeUseCase(aiInsights = get(), cache = get(), clock = get(), analytics = get()) }

    viewModel { (period: SummaryPeriod, language: String) ->
        InsightsViewModel(
            getMetrics = get(),
            generateNarrative = get(),
            appConfig = get(),
            analytics = get(),
            period = period,
            language = language,
        )
    }
}
