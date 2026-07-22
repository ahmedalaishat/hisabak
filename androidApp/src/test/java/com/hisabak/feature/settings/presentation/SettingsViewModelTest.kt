package com.hisabak.feature.settings.presentation

import com.hisabak.core.domain.ThemeMode
import com.hisabak.testutil.FakeAnalytics
import com.hisabak.testutil.FakeAppPreferences
import com.hisabak.testutil.FakeBackupPassphraseStore
import com.hisabak.testutil.MainDispatcherRule
import com.hisabak.testutil.TestClock
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun vm(
        prefs: FakeAppPreferences = FakeAppPreferences(),
        store: FakeBackupPassphraseStore = FakeBackupPassphraseStore(),
        clock: TestClock = TestClock(),
        analytics: FakeAnalytics = FakeAnalytics(),
    ) = SettingsViewModel(prefs, store, clock, analytics)

    @Test
    fun `setThemeMode persists the choice and logs it`() = runTest {
        val prefs = FakeAppPreferences()
        val analytics = FakeAnalytics()
        val viewModel = vm(prefs = prefs, analytics = analytics)
        assertEquals(ThemeMode.SYSTEM, viewModel.themeMode.first())

        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, prefs.themeMode.first())
        assertEquals(listOf("settings_theme_changed"), analytics.names())
    }

    @Test
    fun `onLanguageSelected logs the chosen tag`() = runTest {
        val analytics = FakeAnalytics()
        vm(analytics = analytics).onLanguageSelected("ar")
        assertEquals(listOf("settings_language_changed"), analytics.names())
    }

    @Test
    fun `setAppLockEnabled persists the choice and logs it`() = runTest {
        val prefs = FakeAppPreferences()
        val analytics = FakeAnalytics()
        val viewModel = vm(prefs = prefs, analytics = analytics)

        viewModel.setAppLockEnabled(true)
        advanceUntilIdle()

        assertEquals(true, prefs.appLockEnabled.first())
        assertEquals(listOf("app_lock_toggled"), analytics.names())
    }

    private suspend fun enabledEncryptedPrefs() = FakeAppPreferences().apply {
        setBackupEnabled(true)
        setBackupEncryptionEnabled(true)
    }

    @Test
    fun `passphrase reminder shows when encrypted backup is stale`() = runTest {
        val prefs = enabledEncryptedPrefs()
        val store = FakeBackupPassphraseStore().apply { set("secret123") }
        val viewModel = vm(prefs = prefs, store = store)

        assertTrue(viewModel.passphraseReminderVisible.first()) // confirmedAt = 0 → long overdue
    }

    @Test
    fun `confirming remembered hides the reminder`() = runTest {
        val prefs = enabledEncryptedPrefs()
        val store = FakeBackupPassphraseStore().apply { set("secret123") }
        val viewModel = vm(prefs = prefs, store = store)

        viewModel.confirmPassphraseRemembered()
        advanceUntilIdle()

        assertFalse(viewModel.passphraseReminderVisible.first())
    }

    @Test
    fun `verifying the passphrase returns the result and resets on success`() = runTest {
        val prefs = enabledEncryptedPrefs()
        val store = FakeBackupPassphraseStore().apply { set("secret123") }
        val viewModel = vm(prefs = prefs, store = store)

        var result: Boolean? = null
        viewModel.verifyPassphrase("nope") { result = it }
        advanceUntilIdle()
        assertEquals(false, result)
        assertTrue(viewModel.passphraseReminderVisible.first()) // still due

        viewModel.verifyPassphrase("secret123") { result = it }
        advanceUntilIdle()
        assertEquals(true, result)
        assertFalse(viewModel.passphraseReminderVisible.first()) // reset
    }
}
