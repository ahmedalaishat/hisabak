package com.hisabak.feature.sms.domain

import com.hisabak.core.common.DomainResult
import kotlinx.coroutines.flow.Flow

interface SmsTemplateRepository {
    /** Seeds the defaults on first collection, so an empty table (fresh install or a restore
     *  from a pre-template backup) always yields the shipped set. */
    fun observeAll(): Flow<List<SmsParserTemplate>>
    suspend fun upsert(template: SmsParserTemplate): DomainResult<Unit>
    suspend fun delete(id: SmsTemplateId): DomainResult<Unit>
}
