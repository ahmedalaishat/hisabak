package com.hisabak.feature.sms.domain.capture

/**
 * Where a captured bank message came from. The capture pipeline is open for extension: a new
 * source (e.g. a notification listener, or an iOS share extension once the core moves to KMP)
 * adds a case here plus a thin platform adapter that calls [CaptureTransactionUseCase] — no
 * existing source is touched.
 *
 * [notifiesOnRecord] is true for sources that capture while the user is *outside* the app, where a
 * "transaction recorded" heads-up is useful; false for in-app actions where it would just be noise.
 */
enum class CaptureSource(val notifiesOnRecord: Boolean) {
    /** Android SMS broadcast — auto-capture, present in the sideload build only. */
    SMS_BROADCAST(notifiesOnRecord = true),

    /** Text shared into the app from another app (share sheet, `text/plain`). */
    SHARE(notifiesOnRecord = true),

    /** Text selected in another app and sent via the "process text" action. */
    PROCESS_TEXT(notifiesOnRecord = true),

    /** Pasted by the user inside the SMS inbox. */
    MANUAL_PASTE(notifiesOnRecord = false),
}
