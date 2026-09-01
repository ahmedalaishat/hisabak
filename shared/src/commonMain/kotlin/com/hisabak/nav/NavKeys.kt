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

// forPick: opened from the transaction sheet's "New brand" chip — the created brand is
// published to BrandCreatedBus so the reopened sheet selects it.
data class BrandEditKey(val id: String?, val forPick: Boolean = false) : NavKey
// forPick: opened from the brand editor's "+ New category" chip — the created category is
// published to CategoryCreatedBus so the brand editor underneath selects it on return.
// The prefill fields carry an accepted AI "new category" suggestion into the editor.
data class CategoryEditKey(
    val id: String?,
    val forPick: Boolean = false,
    val prefillName: String? = null,
    val prefillType: String? = null,
    val prefillColor: String? = null,
    val prefillIcon: String? = null,
) : NavKey

// Full-screen child opened from the top-bar bell.
data object NotificationsKey : NavKey

// Full-screen child opened from Settings → Data.
data object BackupKey : NavKey

// Full-screen children for SMS parse templates: the manager (from Settings) and the
// define-by-sample editor (from the manager, or from an unparsed inbox message).
data object SmsTemplatesKey : NavKey
data class SmsTemplateEditKey(val templateId: String?, val sampleSmsId: String?) : NavKey
