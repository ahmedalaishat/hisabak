package com.hisabak.feature.notification.platform

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import com.hisabak.R
import com.hisabak.core.data.preferences.AppLocale
import com.hisabak.feature.notification.domain.NotificationStrings
import java.util.Locale

/**
 * Builds notification copy in the app's chosen language. Notifications are created off the main
 * thread (the SMS path, the budget monitor), so this resolves resources against a locale-wrapped
 * Context directly — without touching the process-global default locale.
 */
class AndroidNotificationStrings(private val context: Context) : NotificationStrings {

    private fun res(): Resources {
        val tag = AppLocale.getLanguageTag(context)
        if (tag.isEmpty()) return context.resources
        val config = Configuration(context.resources.configuration)
        config.setLocale(AppLocale.localeFor(tag))
        return context.createConfigurationContext(config).resources
    }

    override fun transactionRecordedTitle(): String =
        res().getString(R.string.notification_tx_recorded_title)

    override fun transactionRecorded(amount: String, brand: String, category: String): String =
        res().getString(R.string.notification_tx_recorded, amount, brand, category)

    override fun transactionRecordedUncategorized(amount: String, brand: String): String =
        res().getString(R.string.notification_tx_recorded_uncategorized, amount, brand)

    override fun budgetReachedTitle(category: String): String =
        res().getString(R.string.notification_budget_reached_title, category)

    override fun budgetLevelTitle(category: String, level: Int): String =
        res().getString(R.string.notification_budget_level_title, category, level)

    override fun budgetMessage(spent: String, limit: String): String =
        res().getString(R.string.notification_budget_message, spent, limit)
}
