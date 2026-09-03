package com.hisabak.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
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
 * [clearFocusOnTap] at the same host root; neither the nested-scroll observer nor the drag
 * observer consumes anything, so scroll behavior is untouched.
 *
 * **The scroll source alone is not enough to know the user did it.** Compose brings a newly
 * focused field into view by scrolling the ancestor, and dispatches that programmatic scroll as
 * `NestedScrollSource.UserInput` too (`ContentInViewNode`). So focusing a field low enough in a
 * scrollable to need scrolling looked exactly like a drag, and this dismissed the keyboard that
 * the focus had just opened: tapping the transaction sheet's note field opened the keyboard and
 * closed it again immediately, while fields near the top were fine because they need no scroll.
 *
 * Hence the drag flag: a real scroll has pointer events behind it and a bring-into-view has none.
 * Gating on a pointer drag rather than replacing the nested-scroll observer with one also keeps
 * dragging *inside* a field — selecting text — from dismissing anything, since that drag scrolls
 * nothing.
 */
@Composable
fun Modifier.clearFocusOnScroll(): Modifier {
    val dismiss = rememberKeyboardDismiss()
    val drag = remember { DragFlag() }
    val connection = remember(dismiss, drag) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (drag.active && source == NestedScrollSource.UserInput && available != Offset.Zero) {
                    dismiss()
                }
                return Offset.Zero
            }
        }
    }
    return this
        .pointerInput(drag) {
            awaitEachGesture {
                // Initial pass and ignore-consumed throughout: the scrollable underneath consumes
                // the drag, and this needs to observe it without taking it away.
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var travel = 0f
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.changedToUpIgnoreConsumed()) break
                        travel += change.positionChangeIgnoreConsumed().getDistance()
                        if (travel > viewConfiguration.touchSlop) drag.active = true
                    }
                } finally {
                    drag.active = false
                }
            }
        }
        .nestedScroll(connection)
}

/** A plain holder, not snapshot state: only the nested-scroll callback reads it. */
private class DragFlag {
    var active = false
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
