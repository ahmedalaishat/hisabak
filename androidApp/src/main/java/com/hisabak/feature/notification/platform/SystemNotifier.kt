package com.hisabak.feature.notification.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hisabak.MainActivity
import com.hisabak.R
import com.hisabak.feature.notification.domain.Notification
import com.hisabak.feature.notification.domain.Notifier
import com.hisabak.feature.notification.domain.TransactionRecordedAlert

/** Posts Android system notifications for budget alerts and SMS-import confirmations. A no-op
 *  (the in-app record still exists) when the runtime permission isn't granted. */
class SystemNotifier(private val context: Context) : Notifier {

    fun ensureChannel() {
        val res = localizedResources()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                res.getString(R.string.notification_channel_budget_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = res.getString(R.string.notification_channel_budget_desc) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSACTIONS,
                res.getString(R.string.notification_channel_tx_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = res.getString(R.string.notification_channel_tx_desc) },
        )
    }

    private fun localizedResources(): android.content.res.Resources {
        val tag = com.hisabak.core.data.preferences.AppLocale.getLanguageTag(context)
        if (tag.isEmpty()) return context.resources
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(com.hisabak.core.data.preferences.AppLocale.localeFor(tag))
        return context.createConfigurationContext(config).resources
    }

    override fun post(notification: Notification) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CATEGORY_ID, notification.categoryId)
        }
        // One OS-notification id per category so a category's later alerts replace its earlier one.
        val id = notification.categoryId?.hashCode() ?: notification.id.value.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(id, built)
    }

    override fun postTransactionRecorded(alert: TransactionRecordedAlert) {
        if (!hasPermission()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Categorized → focus the category on the dashboard; uncategorized → open the brand editor.
            if (alert.categoryId != null) {
                putExtra(EXTRA_CATEGORY_ID, alert.categoryId)
            } else {
                putExtra(EXTRA_BRAND_ID, alert.brandId)
            }
        }
        val id = alert.transactionId.hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSACTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(alert.title)
            .setContentText(alert.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        // Category glyph tile as the large icon; null (uncategorized) keeps just the app icon.
        categoryGlyphBitmap(context, alert.iconKey, alert.colorKey)?.let(builder::setLargeIcon)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val CHANNEL_ID = "budget_alerts"
        const val CHANNEL_TRANSACTIONS = "transaction_updates"
        const val EXTRA_CATEGORY_ID = "category_id"
        const val EXTRA_BRAND_ID = "brand_id"
    }
}
