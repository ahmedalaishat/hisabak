package com.hisabak.feature.brand.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BrandAliasDao {
    @Query(
        """
        SELECT b.* FROM brands b
        JOIN brand_aliases a ON a.brandId = b.id
        WHERE a.alias = :alias
        LIMIT 1
        """,
    )
    suspend fun findBrandByAlias(alias: String): BrandEntity?

    @Upsert
    suspend fun upsert(entity: BrandAliasEntity)

    @Query("SELECT * FROM brand_aliases")
    suspend fun getAllForBackup(): List<BrandAliasEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<BrandAliasEntity>)

    @Query("DELETE FROM brand_aliases")
    suspend fun deleteAll()
}
