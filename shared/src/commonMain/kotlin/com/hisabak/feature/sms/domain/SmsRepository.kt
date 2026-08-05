package com.hisabak.feature.sms.domain

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.transaction.domain.TransactionId
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

interface SmsRepository {
    fun observeAll(search: String? = null): Flow<List<SmsMessage>>
    suspend fun getById(id: SmsMessageId): DomainResult<SmsMessage>
    suspend fun upsert(message: SmsMessage): DomainResult<Unit>
    suspend fun delete(id: SmsMessageId): DomainResult<Unit>

    /**
     * Clears the link from any message pointing at [transactionId], so a deleted transaction
     * leaves no dangling reference. The message keeps its parse and returns to the inbox as
     * importable.
     */
    suspend fun unlinkTransaction(transactionId: TransactionId): DomainResult<Unit>

    /** True if a message with the same body and received time is already stored. */
    suspend fun existsByContent(body: String, receivedAt: Instant): Boolean
}
