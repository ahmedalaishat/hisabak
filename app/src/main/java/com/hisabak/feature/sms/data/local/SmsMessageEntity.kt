package com.hisabak.feature.sms.data.local

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
)
