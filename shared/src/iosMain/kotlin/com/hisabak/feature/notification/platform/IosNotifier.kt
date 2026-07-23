package com.hisabak.feature.notification.platform

import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.domain.TransactionRecordedAlert
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.notification_budget_level_title
import com.hisabak.shared.resources.notification_budget_message
import com.hisabak.shared.resources.notification_budget_reached_title
import com.hisabak.shared.resources.notification_tx_recorded
import com.hisabak.shared.resources.notification_tx_recorded_title
import com.hisabak.shared.resources.notification_tx_recorded_uncategorized
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/** Ask once for notification permission (the iOS counterpart of the Android 13+ runtime ask). */
fun requestNotificationAuthorization() {
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
    ) { _, _ -> }
}

/** UNUserNotificationCenter-backed [Notifier]: posts local notifications immediately.
 *  Deep-link payloads (category focus / brand edit) are TODO(Phase-B) — a tap just opens the app. */
class IosNotifier : Notifier {

    override fun post(notification: Notification) =
        post(id = notification.id.value, title = notification.title, body = notification.message)

    override fun postTransactionRecorded(alert: TransactionRecordedAlert) =
        post(id = "tx-${alert.transactionId}", title = alert.title, body = alert.message)

    private fun post(id: String, title: String, body: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
        }
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger = null)
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { _ -> }
    }
}

/**
 * Notification copy from the shared CMP string resources (device language — the iOS locale
 * strategy). The [NotificationStrings] port is synchronous while CMP resource reads suspend;
 * the callers run off the main thread (SMS path, budget monitor), so a blocking bundled-file
 * read is acceptable.
 */
class IosNotificationStrings : NotificationStrings {

    private fun s(res: StringResource, vararg args: Any): String =
        runBlocking { getString(res, *args) }

    override fun transactionRecordedTitle(): String = s(Res.string.notification_tx_recorded_title)

    override fun transactionRecorded(amount: String, brand: String, category: String): String =
        s(Res.string.notification_tx_recorded, amount, brand, category)

    override fun transactionRecordedUncategorized(amount: String, brand: String): String =
        s(Res.string.notification_tx_recorded_uncategorized, amount, brand)

    override fun budgetReachedTitle(category: String): String =
        s(Res.string.notification_budget_reached_title, category)

    override fun budgetLevelTitle(category: String, level: Int): String =
        s(Res.string.notification_budget_level_title, category, level)

    override fun budgetMessage(spent: String, limit: String): String =
        s(Res.string.notification_budget_message, spent, limit)
}
