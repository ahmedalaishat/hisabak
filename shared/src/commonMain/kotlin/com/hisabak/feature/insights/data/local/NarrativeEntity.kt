package com.hisabak.feature.insights.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The cached narrative for one period. [payload] is the sanitized items as JSON — a cache row, so a
 * schema for its contents would only be a second place to keep the item shape.
 */
@Entity(tableName = "insight_narratives")
data class NarrativeEntity(
    @PrimaryKey val period: String,
    val narrativeKey: String,
    val payload: String,
    val createdAtMillis: Long,
)
