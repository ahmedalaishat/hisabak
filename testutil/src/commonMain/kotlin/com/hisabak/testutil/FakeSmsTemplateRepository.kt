package com.hisabak.testutil

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsParserTemplate
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSmsTemplateRepository(
    initial: List<SmsParserTemplate> = emptyList(),
) : SmsTemplateRepository {
    private val items = MutableStateFlow(initial)
    val current: List<SmsParserTemplate> get() = items.value

    override fun observeAll(): Flow<List<SmsParserTemplate>> = items

    override suspend fun upsert(template: SmsParserTemplate): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == template.id } + template
        return DomainResult.Success(Unit)
    }

    override suspend fun delete(id: SmsTemplateId): DomainResult<Unit> {
        items.value = items.value.filterNot { it.id == id }
        return DomainResult.Success(Unit)
    }
}

fun parserTemplate(
    id: String = "tpl1",
    pattern: String,
    sampleBody: String? = null,
    isDefault: Boolean = false,
    enabled: Boolean = true,
    createdAtMillis: Long = 0L,
): SmsParserTemplate = SmsParserTemplate(
    id = SmsTemplateId(id),
    pattern = pattern,
    sampleBody = sampleBody,
    isDefault = isDefault,
    enabled = enabled,
    createdAt = Instant.fromEpochMilliseconds(createdAtMillis),
)
