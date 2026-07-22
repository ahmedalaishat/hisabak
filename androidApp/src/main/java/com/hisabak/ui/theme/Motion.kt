package com.hisabak.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing as ComposeEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
 * Hisabak motion — generated from tokens/elevation.css.
 * Calm and quick: standard ease, no bounce/overshoot on functional UI.
 * All motion collapses to 0ms when the user has reduced motion enabled.
 */

object Motion {
    /** Durations in milliseconds. */
    object Duration {
        const val Fast = 120
        const val Base = 200
        const val Slow = 320
    }

    object Easing {
        val Standard: ComposeEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
        val Emphasis: ComposeEasing = CubicBezierEasing(0.3f, 0f, 0f, 1f)
        val Out: ComposeEasing = CubicBezierEasing(0.0f, 0f, 0.2f, 1f)
    }
}

/**
 * True when the OS animator duration scale is 0 (Developer options "Remove animations"
 * or an accessibility reduced-motion setting). Read this to skip scale/translate motion.
 */
val LocalReducedMotion = compositionLocalOf { false }

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/**
 * Duration that respects reduced motion: returns 0 when motion is disabled so callers can
 * keep using the same animation APIs while the result is effectively instant.
 */
@Composable
@ReadOnlyComposable
fun motionDuration(millis: Int): Int =
    if (LocalReducedMotion.current) 0 else millis

/** Standard tween preset for value/color animations, reduced-motion aware. */
@Composable
fun <T> standardTween(
    durationMillis: Int = Motion.Duration.Base,
    easing: ComposeEasing = Motion.Easing.Standard,
): FiniteAnimationSpec<T> = tween(
    durationMillis = motionDuration(durationMillis),
    easing = easing,
)
