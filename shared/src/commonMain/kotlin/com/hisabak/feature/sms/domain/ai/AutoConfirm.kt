package com.hisabak.feature.sms.domain.ai

import com.hisabak.feature.sms.domain.capture.CaptureSource

/**
 * Whether an AI suggestion may become a transaction without the user tapping Confirm.
 *
 * Pure so the policy can be argued with in tests rather than inferred from wiring. Every condition
 * has to hold; the defaults are deliberately timid, because a wrong auto-confirm is silent and a
 * wrong suggestion costs nothing.
 *
 * What this **cannot** do is notice a misreading. Measured on the parse service, a message saying
 * "USD 42.10 (AED 154.62)" was read as 42.10 with the model reporting 0.95 confidence, and the
 * amount really was in the text — so evidence verified and a confidence gate would have passed
 * too. [verifiedPattern] catches invention, not misinterpretation. That is the argument for
 * leaving this off until the eval set gives a measured error rate on real traffic.
 */
fun shouldAutoConfirm(
    enabled: Boolean,
    source: CaptureSource,
    verifiedPattern: String?,
    brandIsKnown: Boolean,
    amountMinor: Long,
    ceilingMinor: Long = AUTO_CONFIRM_CEILING_MINOR,
): Boolean =
    enabled &&
        // The user is holding the phone when they paste; a tap costs them nothing and the
        // suggestion is already on screen.
        source != CaptureSource.MANUAL_PASTE &&
        // Non-null means deriveAiSpans located both the amount and the merchant in the message
        // itself, so neither was invented. It is also what makes the parse worth a template.
        verifiedPattern != null &&
        // Never let an unattended path invent a merchant: a wrong brand created silently is
        // harder to notice, and harder to clean up, than a wrong amount.
        brandIsKnown &&
        amountMinor in 1..ceilingMinor

/** Above this, confirm by hand. A wrong 15 AED is noise; a wrong 15,000 is not. */
const val AUTO_CONFIRM_CEILING_MINOR: Long = 1_000_00L
