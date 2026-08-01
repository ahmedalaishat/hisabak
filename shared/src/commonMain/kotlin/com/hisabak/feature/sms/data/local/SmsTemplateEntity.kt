package com.hisabak.feature.sms.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sms_templates")
data class SmsTemplateEntity(
    @PrimaryKey val id: String,
    val pattern: String,
    /** The message the user defined this template from — lets the editor rebuild the tagged
     *  spans by re-matching the pattern. Null for the seeded defaults. */
    @ColumnInfo(defaultValue = "NULL") val sampleBody: String? = null,
    val isDefault: Boolean,
    val enabled: Boolean = true,
    val createdAtMillis: Long,
)
