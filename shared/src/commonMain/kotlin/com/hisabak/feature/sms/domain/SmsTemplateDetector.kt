package com.hisabak.feature.sms.domain

interface SmsTemplateDetector {
    fun detect(body: String): SmsTemplate?

    /**
     * Suspends until [detect] reflects the stored templates. Capture paths that can run at
     * process start (the iOS Shortcut intent, the SMS broadcast) call this before detecting so
     * user templates aren't missed while the DB snapshot is still loading. Static detectors
     * are ready immediately.
     */
    suspend fun awaitReady() {}
}
