package com.hisabak.nav

import androidx.navigation3.runtime.NavKey

// Top-level destinations — one per bottom-nav tab. Each owns its own back stack.
data object DashboardKey : NavKey
data object TransactionsKey : NavKey
data object SmsKey : NavKey
data object ManageKey : NavKey
data object SettingsKey : NavKey

// Child destinations. IDs are carried as raw strings so the keys stay plain data
// classes; the value-class wrappers are rebuilt at the entry call site.
data class TransactionEditKey(val id: String?) : NavKey
data class BrandEditKey(val id: String?) : NavKey
data class CategoryEditKey(val id: String?) : NavKey

// Full-screen child opened from the top-bar bell.
data object NotificationsKey : NavKey

// Full-screen child opened from Settings → Data.
data object BackupKey : NavKey
