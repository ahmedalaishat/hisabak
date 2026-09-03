package com.hisabak.feature.insights.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One cached narrative, keyed by the digest of what it explained (`narrativeKey`). [payload] is
 * the sanitized items as JSON — a cache row, so a schema for its contents would only be a second
 * place to keep the item shape.
 */
@Entity(tableName = "insight_narratives")
data class NarrativeEntity(
    @PrimaryKey val narrativeKey: String,
    val payload: String,
    val createdAtMillis: Long,
)
