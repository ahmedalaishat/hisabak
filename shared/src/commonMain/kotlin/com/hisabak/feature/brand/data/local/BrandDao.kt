package com.hisabak.feature.brand.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BrandDao {
    @Query(
        """
        SELECT * FROM brands
        WHERE (:search IS NULL OR name LIKE '%' || :search || '%' COLLATE NOCASE)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY LOWER(name)
        """,
    )
    fun observeFiltered(search: String?, categoryId: String?): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands WHERE id = :id")
    suspend fun getById(id: String): BrandEntity?

    @Query(
        """
        SELECT * FROM brands
        WHERE name LIKE '%' || :name || '%' COLLATE NOCASE
           OR :name LIKE '%' || name || '%' COLLATE NOCASE
        LIMIT 1
        """,
    )
    suspend fun findByNameLike(name: String): BrandEntity?

    @Query("SELECT COUNT(*) FROM brands")
    suspend fun count(): Int

    @Query(
        """
        SELECT b.name FROM brands b
        LEFT JOIN transactions t ON t.brandId = b.id
        GROUP BY b.id
        ORDER BY COUNT(t.id) DESC, b.name COLLATE NOCASE
        LIMIT :limit
        """,
    )
    suspend fun namesByUsage(limit: Int): List<String>

    // @Upsert avoids the REPLACE delete+reinsert, which would trip the transactions'
    // ON DELETE RESTRICT foreign key when editing a brand that has transactions.
    @Upsert
    suspend fun upsert(entity: BrandEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BrandEntity>)

    @Query("DELETE FROM brands WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM brands")
    suspend fun getAllForBackup(): List<BrandEntity>

    @Query("DELETE FROM brands")
    suspend fun deleteAll()
}
