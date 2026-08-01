package com.hisabak.feature.notification.domain

/** Port for posting OS-level notifications. Implemented by the platform's `SystemNotifier`;
 *  faked in tests so domain logic stays off the Android framework. */
interface Notifier {
    fun post(notification: Notification)

    /** Post the "transaction recorded" confirmation for an SMS-imported transaction. */
    fun postTransactionRecorded(alert: TransactionRecordedAlert)

    /** A background capture stored a message it couldn't parse — tapping opens the SMS inbox. */
    fun postReviewNeeded(title: String, message: String)
}
