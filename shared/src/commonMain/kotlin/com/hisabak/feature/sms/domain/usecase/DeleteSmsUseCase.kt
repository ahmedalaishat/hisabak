package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository

class DeleteSmsUseCase(private val repository: SmsRepository) {
    suspend operator fun invoke(id: SmsMessageId): DomainResult<Unit> = repository.delete(id)
}
