package com.hisabak.feature.insights.data

import com.hisabak.core.common.SummaryPeriod
import com.hisabak.feature.category.domain.CategoryId
import com.hisabak.feature.insights.data.local.NarrativeDao
import com.hisabak.feature.insights.data.local.NarrativeEntity
import com.hisabak.feature.insights.domain.InsightCategory
import com.hisabak.feature.insights.domain.ai.CachedNarrative
import com.hisabak.feature.insights.domain.ai.NarrativeCache
import com.hisabak.feature.insights.domain.ai.NarrativeInsight
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class RoomNarrativeCache(private val dao: NarrativeDao) : NarrativeCache {

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CachedItem.serializer())

    override suspend fun get(period: SummaryPeriod): CachedNarrative? {
        val row = dao.get(period.name) ?: return null
        // A row this build can't read (an older shape) is a miss, not a crash: it regenerates.
        val items = runCatching { json.decodeFromString(serializer, row.payload) }.getOrNull() ?: return null
        return CachedNarrative(
            period = period,
            key = row.narrativeKey,
            items = items.map { it.toDomain() },
            createdAt = Instant.fromEpochMilliseconds(row.createdAtMillis),
        )
    }

    override suspend fun put(narrative: CachedNarrative) {
        dao.upsert(
            NarrativeEntity(
                period = narrative.period.name,
                narrativeKey = narrative.key,
                payload = json.encodeToString(serializer, narrative.items.map(::CachedItem)),
                createdAtMillis = narrative.createdAt.toEpochMilliseconds(),
            ),
        )
    }

    @Serializable
    private data class CachedItem(
        val categoryId: String?,
        val categoryName: String?,
        val categoryColor: String?,
        val categoryIcon: String?,
        val headline: String,
        val detail: String,
        val suggestedLimitMinor: Long?,
    ) {
        constructor(item: NarrativeInsight) : this(
            categoryId = item.category?.id?.value,
            categoryName = item.category?.name,
            categoryColor = item.category?.color,
            categoryIcon = item.category?.icon,
            headline = item.headline,
            detail = item.detail,
            suggestedLimitMinor = item.suggestedLimitMinor,
        )

        fun toDomain() = NarrativeInsight(
            category = categoryId?.let {
                InsightCategory(CategoryId(it), categoryName ?: "", categoryColor ?: "", categoryIcon ?: "")
            },
            headline = headline,
            detail = detail,
            suggestedLimitMinor = suggestedLimitMinor,
        )
    }
}
