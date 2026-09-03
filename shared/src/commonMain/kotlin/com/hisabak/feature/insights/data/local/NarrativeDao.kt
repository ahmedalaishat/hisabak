package com.hisabak.feature.insights.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface NarrativeDao {
    @Query("SELECT * FROM insight_narratives WHERE period = :period LIMIT 1")
    suspend fun get(period: String): NarrativeEntity?

    @Upsert
    suspend fun upsert(entity: NarrativeEntity)
}
