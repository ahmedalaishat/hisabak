package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsParser
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.rankTemplates
import com.hisabak.feature.sms.domain.template.matchTemplate
import kotlinx.coroutines.flow.first

/**
 * Re-runs the template pipeline over an already-stored message — the follow-through after the
 * user defines a template *from* that message: the format was just taught, so the message
 * imports through it immediately instead of sitting unparsed.
 *
 * Detection reads the template table directly rather than going through the app's observing
 * detector: that detector's compiled snapshot refreshes asynchronously after a save, and the
 * whole point of this use case is to run *immediately* after one — racing the snapshot loses
 * ("No SMS template matched" seconds after teaching the exact format, observed on device).
 *
 * A pending AI suggestion is dropped on success (the template parse supersedes it; a surviving
 * suggestion would wear the "AI parsed" provenance badge on a template-parsed transaction); a
 * failed re-parse leaves the stored message and its suggestion untouched.
 */
class ReparseSmsMessageUseCase(
    private val smsRepository: SmsRepository,
    private val templateRepository: SmsTemplateRepository,
    private val parser: SmsParser,
    private val processor: SmsTransactionProcessor,
) {
    suspend operator fun invoke(id: SmsMessageId): DomainResult<Unit> {
        val message = when (val result = smsRepository.getById(id)) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> return DomainResult.Failure(result.error)
        }
        if (message.isLinked) return DomainResult.Success(Unit)

        val templates = rankTemplates(templateRepository.observeAll().first().filter { it.enabled })
        val template = templates.firstNotNullOfOrNull { candidate ->
            matchTemplate(candidate.pattern, message.body)?.takeIf { plausibleAmount(candidate.pattern, it.fields) }
        } ?: return DomainResult.Failure(DomainError.ValidationFailed("No SMS template matched"))

        val parsed = parser.parse(message.body, template).let { base ->
            if (base.occurredAt == null) base.copy(occurredAt = message.receivedAt) else base
        }
        return processor.commit(message.copy(suggested = null), parsed).map { }
    }

    /** Same guard the runtime detector applies: an amount capture must be a real number. */
    private fun plausibleAmount(pattern: String, fields: Map<String, String>): Boolean {
        if (!pattern.contains("{amount}")) return true
        val value = fields["amount"]?.replace(",", "")?.toDoubleOrNull() ?: return false
        return value > 0
    }
}
