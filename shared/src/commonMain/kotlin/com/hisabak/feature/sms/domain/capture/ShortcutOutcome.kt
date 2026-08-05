package com.hisabak.feature.sms.domain.capture

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult

/**
 * What a background capture (Shortcut / share) should tell the user. [NeedsReview] is the
 * honest name for a ValidationFailed capture: the message *is* stored in the inbox and the
 * detached AI fallback is on it — "couldn't read" undersold what actually happened.
 */
enum class ShortcutOutcome { Recorded, NeedsReview, Duplicate, Failed }

fun shortcutOutcomeFor(result: DomainResult<CaptureResult>): ShortcutOutcome = when (result) {
    is DomainResult.Success -> when (result.value) {
        is CaptureResult.Imported -> ShortcutOutcome.Recorded
        is CaptureResult.StoredUnparsed -> ShortcutOutcome.NeedsReview
    }
    is DomainResult.Failure -> when (result.error) {
        is DomainError.ValidationFailed -> ShortcutOutcome.NeedsReview
        is DomainError.Conflict -> ShortcutOutcome.Duplicate
        else -> ShortcutOutcome.Failed
    }
}
