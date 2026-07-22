package com.hisabak.feature.notification

import com.hisabak.feature.notification.data.RoomCategoryLimitAlertStore
import com.hisabak.feature.notification.data.RoomNotificationRepository
import com.hisabak.feature.notification.domain.CategoryLimitAlertStore
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.domain.NotificationRepository
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import com.hisabak.feature.notification.presentation.list.NotificationsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Pure notification wiring; each platform module binds `Notifier` + `NotificationStrings`. */
val notificationModule = module {
    single<NotificationRepository> { RoomNotificationRepository(dao = get()) }
    single<CategoryLimitAlertStore> { RoomCategoryLimitAlertStore(dao = get()) }

    single {
        CategoryLimitMonitor(
            transactions = get(),
            brands = get(),
            categories = get(),
            limits = get(),
            notifications = get(),
            alertStore = get(),
            systemNotifier = get(),
            currency = get(),
            clock = get(),
            strings = get(),
        )
    }

    single {
        TransactionRecordedNotifier(
            brands = get(),
            categories = get(),
            notifier = get(),
            currency = get(),
            strings = get(),
        )
    }

    viewModel { NotificationsViewModel(repository = get()) }
}
