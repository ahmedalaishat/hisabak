package com.hisabak.feature.dashboard.presentation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot bridge asking the dashboard to focus a category (switch to the Categories tab and
 * expand it), used by notification taps. The dashboard consumes the pending id once and clears
 * it, so it applies even when the dashboard is shown afterwards.
 */
class CategoryFocusBus {
    val pending = MutableStateFlow<String?>(null)

    fun request(categoryId: String) {
        pending.value = categoryId
    }

    fun consume() {
        pending.value = null
    }
}
