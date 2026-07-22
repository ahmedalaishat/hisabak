package com.hisabak.feature.sms.domain

import com.hisabak.core.common.DomainResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SmsRepository {
    fun observeAll(search: String? = null): Flow<List<SmsMessage>>
    suspend fun getById(id: SmsMessageId): DomainResult<SmsMessage>
    suspend fun upsert(message: SmsMessage): DomainResult<Unit>
    suspend fun delete(id: SmsMessageId): DomainResult<Unit>

    /** True if a message with the same body and received time is already stored. */
    suspend fun existsByContent(body: String, receivedAt: Instant): Boolean
}
