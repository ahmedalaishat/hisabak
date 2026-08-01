package com.hisabak.feature.sms.data

import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.data.local.SmsTemplateDao
import com.hisabak.feature.sms.data.local.SmsTemplateEntity
import com.hisabak.feature.sms.domain.DefaultSmsTemplates
import com.hisabak.feature.sms.domain.SmsParserTemplate
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class RoomSmsTemplateRepository(
    private val dao: SmsTemplateDao,
    private val clock: Clock,
) : SmsTemplateRepository {

    override fun observeAll(): Flow<List<SmsParserTemplate>> = flow {
        seedIfEmpty()
        emitAll(
            dao.observeAll()
                // Re-seed on live emissions too: restoring a pre-template backup wipes the
                // table under a long-lived collection (the detector's), and parsing must come
                // back with the defaults rather than go dark until a process restart.
                .onEach { if (it.isEmpty()) seedIfEmpty() }
                .map { list -> list.map { it.toDomain() } },
        )
    }

    override suspend fun upsert(template: SmsParserTemplate): DomainResult<Unit> = runCatchingDomain {
        dao.upsert(template.toEntity())
    }

    override suspend fun delete(id: SmsTemplateId): DomainResult<Unit> = runCatchingDomain {
        dao.deleteById(id.value)
    }

    // Fixed ids keep this idempotent: a re-seed after a wiped restore can't duplicate rows, and
    // an upsert of an already-present default is a no-op REPLACE with identical values.
    private suspend fun seedIfEmpty() {
        if (dao.count() > 0L) return
        dao.upsertAll(
            DefaultSmsTemplates.patterns.mapIndexed { index, pattern ->
                SmsTemplateEntity(
                    id = "default-$index",
                    pattern = pattern,
                    sampleBody = null,
                    isDefault = true,
                    enabled = true,
                    // Staggered so createdAt is a usable tie-break — one shared timestamp
                    // would leave sibling defaults order-ambiguous.
                    createdAtMillis = clock.now().toEpochMilliseconds() + index,
                )
            },
        )
    }

    private inline fun runCatchingDomain(block: () -> Unit): DomainResult<Unit> = try {
        block()
        DomainResult.Success(Unit)
    } catch (e: Exception) {
        DomainResult.Failure(DomainError.Unexpected(e))
    }
}

internal fun SmsTemplateEntity.toDomain() = SmsParserTemplate(
    id = SmsTemplateId(id),
    pattern = pattern,
    sampleBody = sampleBody,
    isDefault = isDefault,
    enabled = enabled,
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
)

internal fun SmsParserTemplate.toEntity() = SmsTemplateEntity(
    id = id.value,
    pattern = pattern,
    sampleBody = sampleBody,
    isDefault = isDefault,
    enabled = enabled,
    createdAtMillis = createdAt.toEpochMilliseconds(),
)
