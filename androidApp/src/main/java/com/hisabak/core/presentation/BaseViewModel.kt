package com.hisabak.core.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface ViewIntent
interface ViewState
interface ViewEffect

abstract class BaseViewModel<INTENT : ViewIntent, STATE : ViewState, EFFECT : ViewEffect> : ViewModel() {

    // Built lazily so initialState() runs on first access — after the subclass constructor has
    // assigned its properties — not during this base constructor. Otherwise initialState() would
    // read still-null constructor params (e.g. `isNew = id == null` would always be true).
    private val _state: MutableStateFlow<STATE> by lazy { MutableStateFlow(initialState()) }
    val state: StateFlow<STATE> get() = _state

    private val _effect = MutableStateFlow<EFFECT?>(null)
    val effect = _effect

    protected fun setState(reducer: STATE.() -> STATE) {
        _state.value = _state.value.reducer()
    }

    protected fun sendEffect(effect: EFFECT) {
        _effect.value = effect
    }

    protected fun clearEffect() {
        _effect.value = null
    }

    abstract fun initialState(): STATE

    abstract fun onIntent(intent: INTENT)
}
