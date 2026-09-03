package com.hisabak.feature.sms.domain.ai

import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.AppPreferences
import com.hisabak.feature.brand.domain.BrandRepository
import com.hisabak.feature.sms.domain.SmsMessage
import com.hisabak.feature.sms.domain.capture.CaptureSource
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import kotlinx.coroutines.flow.first

/**
 * Promotes a stored suggestion into a transaction when [shouldAutoConfirm] allows it.
 *
 * Deliberately built on top of [ConfirmAiSuggestionUseCase] rather than beside it: the confirm path
 * already links the message, records the parse, learns the template, and re-checks category limits.
 * A second write path would drift from it.
 *
 * A confirmed transaction is announced through [TransactionRecordedNotifier] — the app acted while
 * the user was elsewhere, so a silent entry would be indistinguishable from a bug.
 */
class AutoConfirmSuggestionUseCase(
    private val preferences: AppPreferences,
    private val brandRepository: BrandRepository,
    private val confirm: ConfirmAiSuggestionUseCase,
    private val recordedNotifier: TransactionRecordedNotifier,
) {
    /** True when a transaction was created. Never throws: this runs detached from any screen. */
    suspend operator fun invoke(message: SmsMessage, source: CaptureSource): Boolean {
        val suggestion = message.suggested ?: return false
        val amount = suggestion.amount ?: return false
        val brand = suggestion.brandName?.trim().orEmpty()

        // findByNameLike is the same containment rule the commit path uses to link a brand, so
        // "known" here means exactly "confirm would attach this to a brand that already exists"
        // rather than create one.
        val brandIsKnown = brand.isNotEmpty() && brandRepository.findByNameLike(brand) != null

        val allowed = shouldAutoConfirm(
            enabled = preferences.autoConfirmEnabled.first(),
            source = source,
            verifiedPattern = message.suggestedPattern,
            brandIsKnown = brandIsKnown,
            amountMinor = amount.amountMinor,
        )
        if (!allowed) return false

        return when (val result = confirm(message.id)) {
            is DomainResult.Success -> {
                recordedNotifier.notify(result.value.transaction)
                true
            }
            is DomainResult.Failure -> false
        }
    }
}
