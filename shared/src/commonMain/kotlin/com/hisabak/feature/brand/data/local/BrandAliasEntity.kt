package com.hisabak.feature.brand.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A merchant string as a bank writes it, mapped to the brand the user linked it to.
 *
 * Learned when a confirmed AI parse's raw merchant text doesn't resolve to the chosen brand on
 * its own ("GOOGLEYOUTUBE,US" → "Youtube video"), which is exactly the mapping the synthesized
 * regex template would otherwise throw away: the template captures the raw text, so without this
 * the next match creates a duplicate brand.
 *
 * [alias] is lowercased on write and is the primary key, so one merchant string means one brand.
 * CASCADE, because an alias for a deleted brand resolves to nothing.
 */
@Entity(
    tableName = "brand_aliases",
    foreignKeys = [
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("brandId")],
)
data class BrandAliasEntity(
    @PrimaryKey val alias: String,
    val brandId: String,
)
