package com.hisabak.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.navigation3.ui.NavDisplay
import com.hisabak.ui.theme.Motion

/**
 * Per-entry navigation transition for full-screen child destinations: the new screen slides in from
 * the end with a fade while the previous one drifts slightly + fades; the reverse on pop (and during
 * the predictive-back gesture). Applied per entry — not globally — so the bottom-sheet destination
 * and bottom-nav tab switches keep their own behavior.
 */
fun fullScreenTransition(): Map<String, Any> {
    val spec = tween<Float>(Motion.Duration.Slow, easing = Motion.Easing.Standard)
    val slide = tween<androidx.compose.ui.unit.IntOffset>(Motion.Duration.Slow, easing = Motion.Easing.Standard)
    return NavDisplay.transitionSpec {
        (slideInHorizontally(slide) { it } + fadeIn(spec)) togetherWith
            (slideOutHorizontally(slide) { -it / 6 } + fadeOut(spec))
    } + NavDisplay.popTransitionSpec {
        (slideInHorizontally(slide) { -it / 6 } + fadeIn(spec)) togetherWith
            (slideOutHorizontally(slide) { it } + fadeOut(spec))
    } + NavDisplay.predictivePopTransitionSpec { _ ->
        (slideInHorizontally(slide) { -it / 6 } + fadeIn(spec)) togetherWith
            (slideOutHorizontally(slide) { it } + fadeOut(spec))
    }
}
