package com.hisabak.feature.insights.domain.ai

import kotlin.time.Instant

/** A generated narrative under the [key] it was generated for — see [narrativeKey]. */
data class CachedNarrative(
    val key: String,
    val items: List<NarrativeInsight>,
    val createdAt: Instant,
)

/**
 * Narratives by key. A cache, not data: it is not in the backup envelope, and a restored ledger
 * simply misses and regenerates. Implementations prune old rows on write.
 */
interface NarrativeCache {
    suspend fun get(key: String): CachedNarrative?

    suspend fun put(narrative: CachedNarrative)
}
