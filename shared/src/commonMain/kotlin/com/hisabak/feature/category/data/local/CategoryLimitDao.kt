package com.hisabak.feature.category.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryLimitDao {
    @Query("SELECT * FROM category_limits")
    fun observeAll(): Flow<List<CategoryLimitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CategoryLimitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<CategoryLimitEntity>)

    // Backup.
    @Query("SELECT * FROM category_limits")
    suspend fun getAllForBackup(): List<CategoryLimitEntity>

    @Query("DELETE FROM category_limits")
    suspend fun deleteAll()
}
