package com.hisabak.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hisabak.core.common.Clock
import com.hisabak.core.domain.AppPreferences
import com.hisabak.core.domain.ThemeMode
import com.hisabak.core.domain.analytics.Analytics
import com.hisabak.core.domain.analytics.AnalyticsEvent
import com.hisabak.core.domain.backup.BackupPassphraseStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.days

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val passphraseStore: BackupPassphraseStore,
    private val clock: Clock,
    private val analytics: Analytics,
) : ViewModel() {

    val themeMode: Flow<ThemeMode> = preferences.themeMode

    val appLockEnabled: Flow<Boolean> = preferences.appLockEnabled

    /** Show the "do you still remember your backup passphrase?" card once it's been a while. */
    val passphraseReminderVisible: Flow<Boolean> = combine(
        preferences.backupEnabled,
        preferences.backupEncryptionEnabled,
        passphraseStore.isSet,
        preferences.passphraseConfirmedAt,
    ) { enabled, encryption, passphraseSet, confirmedAt ->
        enabled && encryption && passphraseSet &&
            clock.now().toEpochMilli() - confirmedAt > REMINDER_INTERVAL.inWholeMilliseconds
    }

    /** "Yes, I remember" — reset the reminder without asking the user to type it. */
    fun confirmPassphraseRemembered() {
        viewModelScope.launch { preferences.setPassphraseConfirmedAt(clock.now().toEpochMilli()) }
    }

    /** "Let me check" — verify the typed passphrase; on success reset the reminder. */
    fun verifyPassphrase(input: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val correct = passphraseStore.get() == input
            if (correct) preferences.setPassphraseConfirmedAt(clock.now().toEpochMilli())
            onResult(correct)
        }
    }

    fun setThemeMode(value: ThemeMode) {
        analytics.log(AnalyticsEvent.SettingsThemeChanged(value.name.lowercase()))
        viewModelScope.launch { preferences.setThemeMode(value) }
    }

    fun setAppLockEnabled(value: Boolean) {
        analytics.log(AnalyticsEvent.AppLockToggled(value))
        viewModelScope.launch { preferences.setAppLockEnabled(value) }
    }

    /** The locale switch itself happens in the UI layer (AppCompatDelegate); this only records it. */
    fun onLanguageSelected(tag: String) {
        analytics.log(AnalyticsEvent.SettingsLanguageChanged(tag))
    }

    private companion object {
        val REMINDER_INTERVAL = 30.days
    }
}
