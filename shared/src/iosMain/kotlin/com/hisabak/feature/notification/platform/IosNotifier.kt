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

/** UNUserNotificationCenter-backed [Notifier]: posts local notifications immediately, carrying
 *  the same deep-link payload androidApp puts in its Intent extras — the tap handler in
 *  `IosNotificationTapHandler` publishes it to the category-focus / brand-edit buses. */
class IosNotifier : Notifier {

    override fun post(notification: Notification) = post(
        id = notification.id.value,
        title = notification.title,
        body = notification.message,
        categoryId = notification.categoryId,
        brandId = null,
    )

    override fun postTransactionRecorded(alert: TransactionRecordedAlert) = post(
        id = "tx-${alert.transactionId}",
        title = alert.title,
        body = alert.message,
        categoryId = alert.categoryId,
        // Same rule as SystemNotifier: only an uncategorized brand deep-links to its editor.
        brandId = if (alert.categoryId == null) alert.brandId else null,
    )

    override fun postReviewNeeded(title: String, message: String) = post(
        id = "review-needed",
        title = title,
        body = message,
        categoryId = null,
        brandId = null,
        openInbox = true,
    )

    private fun post(
        id: String,
        title: String,
        body: String,
        categoryId: String?,
        brandId: String?,
        openInbox: Boolean = false,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setUserInfo(
                buildMap<Any?, Any?> {
                    categoryId?.let { put(USER_INFO_CATEGORY_ID, it) }
                    brandId?.let { put(USER_INFO_BRAND_ID, it) }
                    if (openInbox) put(USER_INFO_OPEN_INBOX, "1")
                },
            )
        }
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger = null)
        UNUserNotificationCenter.currentNotificationCenter()
            .addNotificationRequest(request) { _ -> }
    }

    companion object {
        const val USER_INFO_CATEGORY_ID = "categoryId"
        const val USER_INFO_BRAND_ID = "brandId"
        const val USER_INFO_OPEN_INBOX = "openInbox"
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

    override fun reviewNeededTitle(): String = s(Res.string.notification_review_title)

    override fun reviewNeededMessage(): String = s(Res.string.notification_review_message)
}
