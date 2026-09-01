package com.hisabak.feature.category.presentation

import com.hisabak.feature.category.domain.CategoryId
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * One-shot bridge handing a just-created category to the brand editor beneath the category
 * editor, so the "+ New category" detour returns with the result selected. Only the
 * `forPick` nav entry publishes; the brand editor consumes the pending id once.
 */
class CategoryCreatedBus {
    val pending = MutableStateFlow<CategoryId?>(null)

    fun publish(id: CategoryId) {
        pending.value = id
    }

    fun consume() {
        pending.value = null
    }
}
