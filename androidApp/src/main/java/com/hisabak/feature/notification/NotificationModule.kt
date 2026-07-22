package com.hisabak.feature.notification

import com.hisabak.feature.notification.data.RoomNotificationRepository
import com.hisabak.feature.notification.domain.CategoryLimitMonitor
import com.hisabak.feature.notification.domain.NotificationRepository
import com.hisabak.feature.notification.domain.NotificationStrings
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.domain.TransactionRecordedNotifier
import com.hisabak.feature.notification.platform.AndroidNotificationStrings
import com.hisabak.feature.notification.platform.SystemNotifier
import com.hisabak.feature.notification.presentation.list.NotificationsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val notificationModule = module {
    single<NotificationRepository> { RoomNotificationRepository(dao = get()) }
    single { SystemNotifier(androidContext()) } bind Notifier::class
    single<NotificationStrings> { AndroidNotificationStrings(androidContext()) }

    single {
        CategoryLimitMonitor(
            transactions = get(),
            brands = get(),
            categories = get(),
            limits = get(),
            notifications = get(),
            alertDao = get(),
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
