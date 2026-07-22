package com.hisabak.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlin.math.roundToLong
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.LocalIndication
import com.hisabak.ui.theme.LocalReducedMotion
import com.hisabak.ui.theme.Motion
import com.hisabak.ui.theme.motionDuration

/**
 * Shrinks the receiver toward [min] while [pressed] is true, using the calm standard ease.
 * No-ops under reduced motion. Read [pressed] from an interaction source.
 */
fun Modifier.pressScale(pressed: Boolean, min: Float = 0.97f): Modifier = composed {
    val target = if (pressed && !LocalReducedMotion.current) min else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = motionDuration(Motion.Duration.Fast),
            easing = Motion.Easing.Standard,
        ),
        label = "pressScale",
    )
    scale(scale)
}

/**
 * The standard Hisabak clickable: a press-scale response plus a light haptic tick on click.
 * Use this everywhere instead of a bare [Modifier.clickable] so feedback is consistent and
 * automatically reduced-motion aware. [pressMin] is 0.97 for buttons/cards, 0.92 for icons.
 */
fun Modifier.hisabakClickable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressMin: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    this
        .pressScale(pressed, pressMin)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
        ) {
            if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/**
 * A filled, pressable surface: the press-scale wraps the whole element (fill, outline, and
 * content scale as one), the ripple stays bounded to [shape], and a light haptic fires on click.
 *
 * Use this for any tappable thing that paints its own [background] — buttons, chips, segments.
 * It exists because [hisabakClickable] embeds the press-scale in the middle of the chain; when a
 * caller paints a background *before* it, the scale's `graphicsLayer` decouples from that fill,
 * which both mangles the scale and (during the press-layer churn) drops the background to the bare
 * window color until the next touch. Baking the order in here keeps the fill inside the scaled
 * layer, mirroring [SurfaceCard].
 */
@Composable
fun Modifier.hisabakPressable(
    shape: Shape,
    background: Color,
    border: BorderStroke? = null,
    enabled: Boolean = true,
    haptic: Boolean = true,
    pressMin: Float = 0.97f,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val hapticFeedback = LocalHapticFeedback.current

    return this
        .pressScale(pressed, pressMin)
        .clip(shape)
        .background(background, shape)
        .then(if (border != null) Modifier.border(border, shape) else Modifier)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
        ) {
            if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Press-scale tuned for icon-only affordances (slightly more pronounced). */
@Composable
fun Modifier.iconPressScale(pressed: Boolean): Modifier = pressScale(pressed, min = 0.92f)

/**
 * Animates a money amount (in minor units) toward [target] with a calm count-up, settling
 * exactly on [target]. Reserve this for hero figures. Returns [target] under reduced motion.
 */
@Composable
fun animatedAmountMinor(target: Long): Long {
    if (LocalReducedMotion.current) return target
    val anim = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        anim.animateTo(
            targetValue = target.toFloat(),
            animationSpec = tween(
                durationMillis = Motion.Duration.Slow,
                easing = Motion.Easing.Standard,
            ),
        )
    }
    return if (anim.isRunning) anim.value.roundToLong() else target
}
