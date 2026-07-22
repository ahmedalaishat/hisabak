package com.hisabak.testutil

import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent

/** Records logged events and screen views so tests can assert on analytics without Firebase. */
class FakeAnalytics : Analytics {
    val logged = mutableListOf<AnalyticsEvent>()
    var lastScreen: String? = null
        private set

    override fun log(event: AnalyticsEvent) {
        logged += event
    }

    override fun setCurrentScreen(name: String) {
        lastScreen = name
    }

    fun names(): List<String> = logged.map { it.name }
}
