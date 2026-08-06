package com.hisabak.feature.brand.presentation

import com.hisabak.feature.brand.domain.BrandId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot bridge handing a just-created brand back to the transaction sheet after the "New
 * brand" detour (the sheet is closed and reopened around the full-screen editor — see the
 * overlay-scene note in HisabakRoot — so the result can't ride the back stack). Only the
 * `forPick` nav entry publishes; the reopened sheet consumes the pending id once.
 */
class BrandCreatedBus {
    val pending = MutableStateFlow<BrandId?>(null)

    fun publish(id: BrandId) {
        pending.value = id
    }

    fun consume() {
        pending.value = null
    }
}
