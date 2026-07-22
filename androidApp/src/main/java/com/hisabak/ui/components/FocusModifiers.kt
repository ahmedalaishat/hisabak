package com.hisabak.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Dismisses the keyboard (clears text-field focus) when the user taps anywhere
 * in this composable that isn't an interactive child. Apply once at a host/screen
 * root for app-wide "tap outside to dismiss" behavior.
 *
 * Tap-only: drags/scrolls and child clicks still work — children consume their
 * own gestures, and this never consumes scroll.
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = { focusManager.clearFocus() })
    }
}
