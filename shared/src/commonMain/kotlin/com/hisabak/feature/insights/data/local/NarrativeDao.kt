package com.hisabak.feature.insights.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface NarrativeDao {
    @Query("SELECT * FROM insight_narratives WHERE narrativeKey = :key LIMIT 1")
    suspend fun get(key: String): NarrativeEntity?

    @Upsert
    suspend fun upsert(entity: NarrativeEntity)

    @Query("DELETE FROM insight_narratives WHERE createdAtMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
