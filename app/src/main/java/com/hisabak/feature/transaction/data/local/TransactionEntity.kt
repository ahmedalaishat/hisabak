package com.hisabak.feature.transaction.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hisabak.feature.brand.data.local.BrandEntity

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = BrandEntity::class,
            parentColumns = ["id"],
            childColumns = ["brandId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("brandId"), Index("occurredAtMillis")],
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val currency: String,
    val brandId: String,
    val note: String?,
    val occurredAtMillis: Long,
    val sourceSmsId: String?,
)
