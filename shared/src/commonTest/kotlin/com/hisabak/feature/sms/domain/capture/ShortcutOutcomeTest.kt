package com.hisabak.feature.sms.domain.capture

import com.hisabak.core.common.DomainError
import com.hisabak.core.common.DomainResult
import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.testutil.aed
import com.hisabak.testutil.transaction
import kotlin.test.Test
import kotlin.test.assertEquals

class ShortcutOutcomeTest {

    @Test
    fun `outcomes map to what actually happened`() {
        assertEquals(
            ShortcutOutcome.Recorded,
            shortcutOutcomeFor(DomainResult.Success(CaptureResult.Imported(transaction(amountMinor = aed(1_00).amountMinor)))),
        )
        assertEquals(
            ShortcutOutcome.NeedsReview,
            shortcutOutcomeFor(DomainResult.Success(CaptureResult.StoredUnparsed(SmsMessageId("s1")))),
        )
        // ValidationFailed from a background source still stores the message + fires the AI
        // fallback — "needs review" is the honest report, not "failed".
        assertEquals(
            ShortcutOutcome.NeedsReview,
            shortcutOutcomeFor(DomainResult.Failure(DomainError.ValidationFailed("No SMS template matched"))),
        )
        assertEquals(
            ShortcutOutcome.Duplicate,
            shortcutOutcomeFor(DomainResult.Failure(DomainError.Conflict("Duplicate SMS ignored"))),
        )
        assertEquals(
            ShortcutOutcome.Failed,
            shortcutOutcomeFor(DomainResult.Failure(DomainError.Unexpected(IllegalStateException()))),
        )
    }
}
