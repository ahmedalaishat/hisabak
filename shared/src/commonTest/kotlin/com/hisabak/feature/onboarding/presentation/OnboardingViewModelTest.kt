package com.hisabak.feature.onboarding.presentation

import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.MainDispatcherTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest : MainDispatcherTest() {

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
