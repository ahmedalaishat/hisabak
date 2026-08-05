package com.hisabak.feature.sms.domain.capture

import com.hisabak.core.common.DomainResult
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import com.hisabak.feature.sms.domain.usecase.IngestSmsUseCase
import kotlin.time.Instant

/**
 * The single funnel every capture source flows through. It parses + persists the raw message
 * (via [IngestSmsUseCase]) and then applies the shared post-capture side effects — the
 * "transaction recorded" confirmation and a budget-limit re-check — governed by the [CaptureSource].
 *
 * Platform adapters (the SMS broadcast receiver, the share / process-text activity, and any future
 * source such as a notification listener or an iOS share extension) stay thin: they only extract
 * text and call this. Adding a source therefore changes nothing here — the side-effect policy lives
 * on [CaptureSource]. Free of Android types, so the funnel and its sources can move to a KMP
 * commonMain module later.
 */
class CaptureTransactionUseCase(
    private val ingest: IngestSmsUseCase,
    private val recordedNotifier: TransactionRecordedNotifier,
    private val limitMonitor: CategoryLimitMonitor,
    private val analytics: Analytics,
) {
    suspend operator fun invoke(
        rawText: String,
        source: CaptureSource,
        receivedAt: Instant? = null,
    ): DomainResult<CaptureResult> {
        val result = ingest(rawText, source, receivedAt)
        when (result) {
            is DomainResult.Success -> when (val outcome = result.value) {
                is CaptureResult.Imported -> {
                    analytics.log(AnalyticsEvent.SmsCaptured(source = source.name.lowercase(), amount = outcome.transaction.amount))
                    if (source.notifiesOnRecord) recordedNotifier.notify(outcome.transaction)
                    // Re-check budgets now, while we hold the captured transaction — covers background
                    // captures (SMS broadcast / share) where the app-scoped monitor may not run. Idempotent.
                    limitMonitor.evaluateNow()
                }
                // Stored for the confirm-first flow — no transaction yet, so no notifier or
                // budget re-check; keep the analytics continuity of the old failure signal.
                is CaptureResult.StoredUnparsed ->
                    analytics.log(AnalyticsEvent.SmsParseFailed(reason = "validationfailed"))
            }
            is DomainResult.Failure -> {
                // The error *type* only — never the raw message text, to keep analytics PII-free.
                analytics.log(AnalyticsEvent.SmsParseFailed(reason = result.error::class.simpleName?.lowercase() ?: "unknown"))
                // Background captures have no screen: the "saved for review" notification is
                // the only honest feedback (the message IS stored, with the AI fallback on it).
                if (source.notifiesOnRecord && result.error is com.hisabak.core.common.DomainError.ValidationFailed) {
                    recordedNotifier.notifyReviewNeeded()
                }
            }
        }
        return result
    }
}
