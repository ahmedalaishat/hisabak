package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTransactionProcessor

/**
 * Re-runs the template pipeline over an already-stored message — the follow-through after the
 * user defines a template *from* that message: the format was just taught, so the message
 * imports through it immediately instead of sitting unparsed. A pending AI suggestion is
 * dropped on success (the template parse supersedes it; a surviving suggestion would wear the
 * "AI parsed" provenance badge on a template-parsed transaction). No template matching is not
 * an error worth surfacing — the message just stays as it was.
 */
class ReparseSmsMessageUseCase(
    private val smsRepository: SmsRepository,
    private val processor: SmsTransactionProcessor,
) {
    suspend operator fun invoke(id: SmsMessageId): DomainResult<Unit> {
        val message = when (val result = smsRepository.getById(id)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        }
        if (message.isLinked) return DomainResult.Success(Unit)
        // The suggestion is only cleared in the message copy handed to the processor — commit
        // persists it on success; a failed parse leaves the stored message (and its
        // suggestion) untouched.
        return processor.process(message.copy(suggested = null), defaultDate = message.receivedAt).map { }
    }
}
