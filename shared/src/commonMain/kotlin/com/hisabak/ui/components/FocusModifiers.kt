package com.hisabak.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

/**
 * Dismisses the keyboard the moment any descendant scrollable starts a user-driven scroll —
 * scrolling means reading, not typing, and the keyboard covers half the content. Pairs with
 * [clearFocusOnTap] at the same host root; a nested-scroll observer never consumes anything,
 * so scroll behavior is untouched.
 */
@Composable
fun Modifier.clearFocusOnScroll(): Modifier {
    val focusManager = LocalFocusManager.current
    val connection = remember(focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}
