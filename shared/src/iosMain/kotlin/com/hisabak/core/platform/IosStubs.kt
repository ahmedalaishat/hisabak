package com.hisabak.core.platform

import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent

/** The last Phase A stub: analytics stays a no-op until the B6 decision
 *  (Firebase via gitlive vs. keeping iOS analytics off). TODO(Phase-B) */
class NoopAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit
    override fun setCurrentScreen(name: String) = Unit
}
