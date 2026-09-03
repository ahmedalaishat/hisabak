package com.hisabak.testutil

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.insights.domain.ai.CachedNarrative
import com.hisabak.feature.insights.domain.ai.NarrativeCache

class FakeNarrativeCache : NarrativeCache {
    val entries = mutableMapOf<SummaryPeriod, CachedNarrative>()

    override suspend fun get(period: SummaryPeriod): CachedNarrative? = entries[period]

    override suspend fun put(narrative: CachedNarrative) {
        entries[narrative.period] = narrative
    }
}
