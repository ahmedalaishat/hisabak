package com.hisabak.feature.sms.domain.template

import com.hisabak.core.common.Clock
import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.sms.domain.SmsParserTemplate
import com.hisabak.feature.sms.domain.SmsTemplateId
import com.hisabak.feature.sms.domain.SmsTemplateRepository
import kotlinx.coroutines.flow.first

/**
 * Installs a template derived from a confirmed AI parse, so the next message of that bank format
 * parses offline with no model involved.
 *
 * Declining is a **success with a null value**, not a failure: the caller has already created the
 * user's transaction, and a template that can't be trusted simply isn't installed. Every rejection
 * reason is logged so the skip mix stays visible.
 */
class SynthesizeTemplateUseCase(
    private val repository: SmsTemplateRepository,
    private val saveTemplate: SaveSmsTemplateUseCase,
    private val previewTemplate: PreviewSmsTemplateUseCase,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(body: String, pattern: String): DomainResult<SmsParserTemplate?> {
        val spans = reconstructSpans(pattern, body) ?: return skip("no_amount_span")

        // The same gate hand-made templates pass, so a learned rule can never be looser than one
        // the user could have written: amount present and numeric, 10+ literal anchor chars.
        saveTemplate.validate(body, spans)?.let { return skip("weak_anchor") }

        if (repository.observeAll().first().any { it.pattern == pattern }) return skip("duplicate")

        // Over-generic patterns show up as messages this rule would match but extract a different
        // amount for than the one already stored against them.
        if (previewTemplate(pattern, body).conflicts > 0) return skip("conflict")

        val template = SmsParserTemplate(
            id = SmsTemplateId.new(),
            pattern = pattern,
            sampleBody = body,
            isDefault = false,
            enabled = true,
            createdAt = clock.now(),
            derivedByAi = true,
        )
        return repository.upsert(template).map {
            analytics.log(AnalyticsEvent.SmsTemplateSynthesized)
            template
        }
    }

    private fun skip(reason: String): DomainResult<SmsParserTemplate?> {
        analytics.log(AnalyticsEvent.SmsTemplateSynthesisSkipped(reason))
        return DomainResult.Success(null)
    }
}
