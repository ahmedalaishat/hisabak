package com.hisabak.feature.sms.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsTemplateDao {

    @Query("SELECT * FROM sms_templates ORDER BY isDefault ASC, createdAtMillis ASC")
    fun observeAll(): Flow<List<SmsTemplateEntity>>

    @Query("SELECT COUNT(*) FROM sms_templates")
    suspend fun count(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(template: SmsTemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(templates: List<SmsTemplateEntity>)

    @Query("DELETE FROM sms_templates WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM sms_templates")
    suspend fun getAllForBackup(): List<SmsTemplateEntity>

    @Query("DELETE FROM sms_templates")
    suspend fun deleteAll()
}
