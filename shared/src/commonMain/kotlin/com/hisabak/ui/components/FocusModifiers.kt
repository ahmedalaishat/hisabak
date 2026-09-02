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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Retracts the soft keyboard and drops text-field focus.
 *
 * Both halves are needed: clearing focus alone does not reliably dismiss the keyboard on every
 * platform, which is why the transaction sheet already asks the keyboard controller directly.
 */
@Composable
private fun rememberKeyboardDismiss(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboard) {
        {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
}

/**
 * Dismisses the keyboard when the user taps anywhere in this composable that isn't an interactive
 * child. Apply once at a host/screen root for app-wide "tap outside to dismiss" behavior.
 *
 * Tap-only: drags/scrolls and child clicks still work — children consume their own gestures, and
 * this never consumes scroll.
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val dismiss = rememberKeyboardDismiss()
    return this.pointerInput(Unit) {
        detectTapGestures(onTap = { dismiss() })
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
    val dismiss = rememberKeyboardDismiss()
    val connection = remember(dismiss) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) {
                    dismiss()
                }
                return Offset.Zero
            }
        }
    }
    return this.nestedScroll(connection)
}

/**
 * Both of the above, for a surface that needs its own copy.
 *
 * A `ModalBottomSheet` renders as its own overlay layer rather than under the app shell, so it
 * never inherits the root's handlers — every sheet containing a text field has to opt in. Build
 * this **inside** the sheet's content lambda: as a call-site argument it would capture the host
 * composition's focus manager and keyboard controller, and dismiss nothing.
 */
@Composable
fun Modifier.dismissKeyboardOnGesture(): Modifier = this.clearFocusOnTap().clearFocusOnScroll()
