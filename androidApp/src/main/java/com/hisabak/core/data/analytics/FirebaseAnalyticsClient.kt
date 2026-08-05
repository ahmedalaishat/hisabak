package com.hisabak.core.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent

/**
 * Firebase-backed [Analytics]. Translates the PII-safe [AnalyticsEvent] catalogue into Firebase
 * `logEvent` calls. Collection itself is enabled/disabled in `HisabakApp` (off in debug builds).
 */
class FirebaseAnalyticsClient(context: Context) : Analytics {

    private val firebase = FirebaseAnalytics.getInstance(context)

    override fun log(event: AnalyticsEvent) {
        firebase.logEvent(event.name, event.params.toBundle())
    }

    override fun setCurrentScreen(name: String) {
        firebase.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply { putString(FirebaseAnalytics.Param.SCREEN_NAME, name) },
        )
    }

    private fun Map<String, Any?>.toBundle(): Bundle = Bundle().apply {
        forEach { (key, value) ->
            when (value) {
                null -> Unit
                is String -> putString(key, value)
                is Boolean -> putString(key, value.toString())
                is Int -> putLong(key, value.toLong())
                is Long -> putLong(key, value)
                is Double -> putDouble(key, value)
                else -> putString(key, value.toString())
            }
        }
    }
}
