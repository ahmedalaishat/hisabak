package com.hisabak.feature.brand.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hisabak.feature.category.data.local.CategoryEntity

@Entity(
    tableName = "brands",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId")],
)
data class BrandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String?,
)
