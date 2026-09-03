package com.hisabak.feature.sms.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_messages",
    indices = [Index("transactionId"), Index("receivedAtMillis")],
)
data class SmsMessageEntity(
    @PrimaryKey val id: String,
    val body: String,
    val receivedAtMillis: Long,
    val transactionId: String?,
    val parsedBrandName: String?,
    val parsedAmountMinor: Long?,
    val parsedCurrency: String?,
    val parsedOccurredAtMillis: Long?,
    @ColumnInfo(defaultValue = "NULL") val suggestedBrandName: String? = null,
    @ColumnInfo(defaultValue = "NULL") val suggestedAmountMinor: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val suggestedCurrency: String? = null,
    @ColumnInfo(defaultValue = "NULL") val suggestedOccurredAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "NULL") val suggestedPattern: String? = null,
    @ColumnInfo(defaultValue = "0") val autoConfirmed: Boolean = false,
)
