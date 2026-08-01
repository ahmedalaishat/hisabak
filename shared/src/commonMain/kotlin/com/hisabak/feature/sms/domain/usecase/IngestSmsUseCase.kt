package com.hisabak.feature.sms.domain.usecase

import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTransactionProcessor
import com.hisabak.feature.sms.domain.ai.SuggestAiParseUseCase
import com.hisabak.feature.sms.domain.capture.CaptureResult
import com.hisabak.feature.sms.domain.capture.CaptureSource
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class IngestSmsUseCase(
    private val smsRepository: SmsRepository,
    private val processor: SmsTransactionProcessor,
    private val clock: Clock,
    private val suggestAiParse: SuggestAiParseUseCase,
    private val appScope: CoroutineScope,
) {
    suspend operator fun invoke(
        body: String,
        source: CaptureSource,
        receivedAt: Instant? = null,
    ): DomainResult<CaptureResult> {
        // Broadcasts can be redelivered; (body, receivedAt) is a stable key, so skip a capture
        // we already stored. Manual paste passes no receivedAt and is never deduped.
        if (receivedAt != null && smsRepository.existsByContent(body, receivedAt)) {
            return DomainResult.Failure(DomainError.Conflict("Duplicate SMS ignored"))
        }
        val occurredFallback = receivedAt ?: clock.now()
        val message = SmsMessage(
            id = SmsMessageId.new(),
            body = body,
            receivedAt = occurredFallback,
        )
        // Pass the received time so an SMS with no parseable date is dated when it arrived,
        // not at clock.now().
        val result = smsRepository.upsert(message)
            .flatMap { processor.process(message, defaultDate = occurredFallback) }
        if (result is DomainResult.Failure && result.error is DomainError.ValidationFailed) {
            // No template matched, but the message is stored. The paste flow has a screen to
            // drive: it gets the id back as a success and runs the AI suggestion itself (with
            // a visible spinner). Background sources keep the detached confirm-first fallback
            // and today's failure contract — their adapters have no UI to update.
            if (source == CaptureSource.MANUAL_PASTE) {
                return DomainResult.Success(CaptureResult.StoredUnparsed(message.id))
            }
            appScope.launch { suggestAiParse(message.id, source = "auto") }
        }
        return result.map { CaptureResult.Imported(it) }
    }
}
