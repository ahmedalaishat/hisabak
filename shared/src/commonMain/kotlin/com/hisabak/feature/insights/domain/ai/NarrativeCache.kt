package com.hisabak.feature.insights.domain.ai

import com.hisabak.core.common.SummaryPeriod
import kotlin.time.Instant

/** A generated narrative with the [key] it was generated for — see [narrativeKey]. */
data class CachedNarrative(
    val period: SummaryPeriod,
    val key: String,
    val items: List<NarrativeInsight>,
    val createdAt: Instant,
)

/**
 * One narrative per period. A cache, not data: it is not in the backup envelope, and a restored
 * ledger simply misses and regenerates.
 */
interface NarrativeCache {
    suspend fun get(period: SummaryPeriod): CachedNarrative?

    suspend fun put(narrative: CachedNarrative)
}
