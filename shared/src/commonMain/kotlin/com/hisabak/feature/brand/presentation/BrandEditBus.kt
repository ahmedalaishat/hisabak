package com.hisabak.feature.brand.presentation

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot bridge asking the app to open a brand's editor (switch to Manage and push the brand
 * edit screen), used by the "transaction recorded" notification when the imported brand is
 * uncategorized. The navigator consumes the pending id once and clears it.
 */
class BrandEditBus {
    val pending = MutableStateFlow<String?>(null)

    fun request(brandId: String) {
        pending.value = brandId
    }

    fun consume() {
        pending.value = null
    }
}
