package com.hisabak.feature.onboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val preferences: AppPreferences,
    private val analytics: Analytics,
) : ViewModel() {

    /** Marks onboarding done; the app's first-launch gate then swaps to the main UI. */
    fun complete() {
        analytics.log(AnalyticsEvent.OnboardingCompleted)
        viewModelScope.launch { preferences.setOnboardingCompleted(true) }
    }
}
