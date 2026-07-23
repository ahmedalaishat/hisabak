package com.hisabak.feature.notification.platform

import com.hisabak.feature.brand.presentation.BrandEditBus
import com.hisabak.feature.dashboard.presentation.CategoryFocusBus
import org.koin.mp.KoinPlatform
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptionSound
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * The iOS counterpart of `MainActivity.handleFocusIntent`: a notification tap publishes its
 * deep-link payload to the same buses the shared nav layer already reacts to (dashboard focus /
 * brand editor). Also presents banners while the app is foregrounded — parity with Android,
 * where system notifications always show.
 */
private class IosNotificationTapHandler : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val userInfo = didReceiveNotificationResponse.notification.request.content.userInfo
        val koin = KoinPlatform.getKoin()
        (userInfo[IosNotifier.USER_INFO_CATEGORY_ID] as? String)
            ?.let { koin.get<CategoryFocusBus>().request(it) }
        (userInfo[IosNotifier.USER_INFO_BRAND_ID] as? String)
            ?.let { koin.get<BrandEditBus>().request(it) }
        withCompletionHandler()
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(
            UNNotificationPresentationOptionBanner or
                UNNotificationPresentationOptionList or
                UNNotificationPresentationOptionSound,
        )
    }
}

// The center holds a weak delegate reference; keep the handler alive for the app's lifetime.
private var retainedHandler: IosNotificationTapHandler? = null

internal fun installNotificationTapHandler() {
    val handler = IosNotificationTapHandler()
    retainedHandler = handler
    UNUserNotificationCenter.currentNotificationCenter().delegate = handler
}
