package com.hisabak.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class BaseViewModelTest {

    private data class SampleState(val isNew: Boolean) : ViewState
    private sealed interface SampleIntent : ViewIntent
    private sealed interface SampleEffect : ViewEffect

    // Mirrors the real edit ViewModels: initialState() reads a constructor property.
    private class SampleViewModel(private val id: String?) :
        BaseViewModel<SampleIntent, SampleState, SampleEffect>() {
        override fun initialState() = SampleState(isNew = id == null)
        override fun onIntent(intent: SampleIntent) = Unit
    }

    @Test
    fun `initialState reads constructor properties, not their pre-init defaults`() {
        // Before lazy state init, initialState() ran in the base constructor while `id` was still
        // null, so isNew was always true. It must now reflect the actual constructor argument.
        assertEquals(false, SampleViewModel(id = "x").state.value.isNew)
        assertEquals(true, SampleViewModel(id = null).state.value.isNew)
    }
}
