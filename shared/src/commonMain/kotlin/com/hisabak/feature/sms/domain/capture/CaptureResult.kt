package com.hisabak.feature.sms.domain.capture

import com.hisabak.feature.sms.domain.SmsMessageId
import com.hisabak.feature.transaction.domain.Transaction

/**
 * What a capture produced. [StoredUnparsed] is a *success*, not a failure: the text is safely
 * in the inbox, it just matched no template — the paste flow uses the id to drive a
 * confirm-first AI suggestion on that row instead of showing a dead-end error.
 */
sealed interface CaptureResult {
    data class Imported(val transaction: Transaction) : CaptureResult
    data class StoredUnparsed(val messageId: SmsMessageId) : CaptureResult
}
