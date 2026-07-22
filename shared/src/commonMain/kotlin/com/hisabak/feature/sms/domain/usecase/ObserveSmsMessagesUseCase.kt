package com.hisabak.feature.sms.domain.usecase

import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsRepository
import kotlinx.coroutines.flow.Flow

class ObserveSmsMessagesUseCase(private val repository: SmsRepository) {
    operator fun invoke(search: String? = null): Flow<List<SmsMessage>> =
        repository.observeAll(search)
}
