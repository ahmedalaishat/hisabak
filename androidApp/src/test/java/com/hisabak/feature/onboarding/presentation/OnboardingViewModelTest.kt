package com.hisabak.feature.onboarding.presentation

import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `complete marks onboarding done`() = runTest {
        val prefs = FakeAppPreferences(initial = false)
        val analytics = FakeAnalytics()
        val vm = OnboardingViewModel(prefs, analytics)
        assertFalse(prefs.onboardingCompleted.first())

        vm.complete()
        advanceUntilIdle()

        assertTrue(prefs.onboardingCompleted.first())
        assertEquals(listOf("onboarding_completed"), analytics.names())
    }
}
