package com.hisabak.feature.sms.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query(
        """
        SELECT * FROM sms_messages
        WHERE (:search IS NULL OR body LIKE '%' || :search || '%' COLLATE NOCASE)
        ORDER BY receivedAtMillis DESC
        """,
    )
    fun observeFiltered(search: String?): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: String): SmsMessageEntity?

    @Query("SELECT COUNT(*) FROM sms_messages")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM sms_messages WHERE body = :body AND receivedAtMillis = :receivedAtMillis")
    suspend fun countByContent(body: String, receivedAtMillis: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmsMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SmsMessageEntity>)

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM sms_messages")
    suspend fun getAllForBackup(): List<SmsMessageEntity>

    @Query("DELETE FROM sms_messages")
    suspend fun deleteAll()
}
