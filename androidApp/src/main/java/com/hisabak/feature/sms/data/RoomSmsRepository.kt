package com.hisabak.feature.sms.data

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.data.local.SmsDao
import com.hisabak.feature.sms.data.local.toDomain
import com.hisabak.feature.sms.data.local.toEntity
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class RoomSmsRepository(
    private val dao: SmsDao,
) : SmsRepository {

    override fun observeAll(search: String?): Flow<List<SmsMessage>> =
        dao.observeFiltered(search?.takeIf(String::isNotBlank))
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: SmsMessageId): DomainResult<SmsMessage> =
        dao.getById(id.value)
            ?.toDomain()
            ?.let { DomainResult.Success(it) }
            ?: DomainResult.Failure(DomainError.NotFound("SmsMessage", id.value))

    override suspend fun upsert(message: SmsMessage): DomainResult<Unit> {
        dao.upsert(message.toEntity())
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: SmsMessageId): DomainResult<Unit> {
        dao.deleteById(id.value)
        return DomainResult.Success(Unit)
    }

    override suspend fun existsByContent(body: String, receivedAt: Instant): Boolean =
        dao.countByContent(body, receivedAt.toEpochMilli()) > 0
}
