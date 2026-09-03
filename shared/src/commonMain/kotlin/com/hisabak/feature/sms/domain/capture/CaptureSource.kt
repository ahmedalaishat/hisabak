package com.hisabak.feature.sms.domain.capture

/**
 * Where a captured bank message came from. The capture pipeline is open for extension: a new
 * source (e.g. a notification listener, or an iOS share extension) adds a case here plus a thin
 * platform adapter that calls [CaptureTransactionUseCase] — no existing source is touched.
 *
 * [notifiesOnRecord] is true for sources that capture while the user is *outside* the app, where a
 * "transaction recorded" heads-up is useful; false for in-app actions where it would just be noise.
 *
 * [awaitsAiFallback] is true where something reports the outcome back to the user the moment the
 * call returns — a Shortcut result, a share-sheet toast. Those must wait for the AI fallback, or
 * they announce "saved for review" while the parse that would have contradicted them is still
 * running. Only a broadcast has nobody waiting: it has no UI at all and the notification is the
 * channel, so it keeps the detached fallback.
 */
enum class CaptureSource(val notifiesOnRecord: Boolean, val awaitsAiFallback: Boolean = true) {
    /** Android SMS broadcast — auto-capture, present in the sideload build only. */
    SMS_BROADCAST(notifiesOnRecord = true, awaitsAiFallback = false),

    /** Text shared into the app from another app (share sheet, `text/plain`). */
    SHARE(notifiesOnRecord = true),

    /** Text selected in another app and sent via the "process text" action. */
    PROCESS_TEXT(notifiesOnRecord = true),

    /** Pasted by the user inside the SMS inbox. */
    /** The inbox drives its own suggestion with a visible spinner, so ingest returns early. */
    MANUAL_PASTE(notifiesOnRecord = false, awaitsAiFallback = false),

    /**
     * iOS Shortcuts "Capture transaction" App Intent — typically fired by a
     * "When I get a message" personal automation, the closest iOS gets to auto-capture.
     */
    SHORTCUT(notifiesOnRecord = true),
}
