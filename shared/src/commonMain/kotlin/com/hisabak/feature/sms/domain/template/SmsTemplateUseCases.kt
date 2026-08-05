package com.hisabak.feature.sms.domain.template

import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.core.common.Money
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.sms.domain.SmsParserTemplate
import com.hisabak.feature.sms.domain.SmsRepository
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import com.hisabak.feature.sms.domain.literalAnchorLength
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveSmsTemplatesUseCase(private val repository: SmsTemplateRepository) {
    operator fun invoke(): Flow<List<SmsParserTemplate>> = repository.observeAll()
}

/** What [SaveSmsTemplateUseCase] rejected — the editor maps these to inline guidance. */
enum class TemplateValidationError { MissingAmount, InvalidAmount, InsufficientAnchor }

class SaveSmsTemplateUseCase(
    private val repository: SmsTemplateRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    /** Validates the tagged sample, derives the pattern, and creates or replaces the template. */
    suspend operator fun invoke(
        existingId: SmsTemplateId?,
        sample: String,
        spans: List<TagSpan>,
    ): DomainResult<SmsParserTemplate> {
        validate(sample, spans)?.let {
            return DomainResult.Failure(DomainError.ValidationFailed(it.name))
        }
        val all = repository.observeAll().first()
        val existing = existingId?.let { id -> all.firstOrNull { it.id == id } }
        if (existing?.isDefault == true) {
            return DomainResult.Failure(DomainError.ValidationFailed("Default templates can't be edited"))
        }
        val pattern = deriveTemplatePattern(sample, spans)
        // Creating a template identical to a stored one (e.g. re-taught from a second message of
        // the same format) reuses the existing row instead of stacking duplicates — success from
        // the caller's view, so the save-and-import flow still imports the message.
        if (existingId == null) {
            all.firstOrNull { it.pattern == pattern }?.let { duplicate ->
                return DomainResult.Success(duplicate)
            }
        }
        val template = SmsParserTemplate(
            id = existingId ?: SmsTemplateId.new(),
            pattern = pattern,
            sampleBody = sample,
            isDefault = false,
            // An edit keeps the row's identity: a disabled template stays disabled, and the
            // creation time (a ranking tie-break) doesn't reset.
            enabled = existing?.enabled ?: true,
            createdAt = existing?.createdAt ?: clock.now(),
        )
        return repository.upsert(template).map {
            analytics.log(AnalyticsEvent.SmsTemplateCreated(edited = existingId != null))
            template
        }
    }

    fun validate(sample: String, spans: List<TagSpan>): TemplateValidationError? {
        val amountSpans = spans.filter { it.role == TagRole.AMOUNT }
        if (amountSpans.isEmpty()) return TemplateValidationError.MissingAmount
        val amountText = amountSpans.joinToString("") { sample.substring(it.start, it.end) }
        if (amountText.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 } == null) {
            return TemplateValidationError.InvalidAmount
        }
        val pattern = deriveTemplatePattern(sample, spans)
        if (literalAnchorLength(pattern) < MIN_ANCHOR_CHARS) {
            return TemplateValidationError.InsufficientAnchor
        }
        return null
    }

    companion object {
        /** Non-whitespace literal chars a template must keep — kills "{amount} at {brand}". */
        const val MIN_ANCHOR_CHARS = 10
    }
}

class DeleteSmsTemplateUseCase(
    private val repository: SmsTemplateRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(id: SmsTemplateId): DomainResult<Unit> {
        val template = repository.observeAll().first().firstOrNull { it.id == id }
            ?: return DomainResult.Failure(DomainError.NotFound("SmsParserTemplate", id.value))
        if (template.isDefault) {
            return DomainResult.Failure(DomainError.ValidationFailed("Default templates can't be deleted"))
        }
        return repository.delete(id).map { analytics.log(AnalyticsEvent.SmsTemplateDeleted) }
    }
}

class SetSmsTemplateEnabledUseCase(
    private val repository: SmsTemplateRepository,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(id: SmsTemplateId, enabled: Boolean): DomainResult<Unit> {
        val template = repository.observeAll().first().firstOrNull { it.id == id }
            ?: return DomainResult.Failure(DomainError.NotFound("SmsParserTemplate", id.value))
        if (template.enabled == enabled) return DomainResult.Success(Unit)
        return repository.upsert(template.copy(enabled = enabled)).map {
            analytics.log(AnalyticsEvent.SmsTemplateToggled(enabled))
        }
    }
}

/** How widely a draft template would match the stored inbox — shown before saving. */
data class TemplatePreview(val matches: Int, val conflicts: Int)

class PreviewSmsTemplateUseCase(private val smsRepository: SmsRepository) {
    /**
     * [sample] is excluded from the counts (it's usually itself a stored message). A conflict
     * is a matched message that already has a parsed or confirmed amount differing from what
     * the draft extracts — the clearest sign the draft is over-generic.
     */
    suspend operator fun invoke(pattern: String, sample: String): TemplatePreview {
        val messages = smsRepository.observeAll().first().filter { it.body != sample }
        var matches = 0
        var conflicts = 0
        for (message in messages) {
            val template = matchTemplate(pattern, message.body) ?: continue
            matches++
            val existingAmount = message.parsed?.amount ?: continue
            // Money.parseMajor, not double math: (0.29 * 100).toLong() truncates to 28 and
            // would flag identical amounts as conflicts.
            val extracted = template.fields["amount"]
                ?.let { Money.parseMajor(it.replace(",", ""), existingAmount.currency) }
            if (extracted == null || extracted.amountMinor != existingAmount.amountMinor) {
                conflicts++
            }
        }
        return TemplatePreview(matches = matches, conflicts = conflicts)
    }
}
