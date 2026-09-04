package com.hisabak.testutil

import com.hisabak.feature.insights.domain.ai.CachedNarrative
import com.hisabak.feature.insights.domain.ai.NarrativeCache

class FakeNarrativeCache : NarrativeCache {
    val entries = mutableMapOf<String, CachedNarrative>()

    override suspend fun get(key: String): CachedNarrative? = entries[key]

    override suspend fun put(narrative: CachedNarrative) {
        entries[narrative.key] = narrative
    }
}
