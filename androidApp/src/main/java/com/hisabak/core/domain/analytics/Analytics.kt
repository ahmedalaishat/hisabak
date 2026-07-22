package com.hisabak.core.domain.analytics

/**
 * Product analytics sink. A thin domain abstraction over the platform analytics SDK so use cases
 * and ViewModels stay testable (see `FakeAnalytics`) and never touch Firebase types directly.
 *
 * Collection is gated on `!BuildConfig.DEBUG` at the SDK level (see `HisabakApp`), so these calls
 * are no-ops in debug builds. The implementation is responsible for keeping events PII-free — but
 * every event built via [AnalyticsEvent] already is, by construction.
 */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** Records a `screen_view` for the named screen (this is a single-Activity Compose app, so
     *  Firebase's automatic Activity/Fragment screen tracking doesn't fire — we report manually). */
    fun setCurrentScreen(name: String)
}
