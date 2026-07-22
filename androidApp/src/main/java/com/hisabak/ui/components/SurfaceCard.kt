package com.hisabak.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.Spacing

/**
 * White-ish panel with a subtle outline — the base container used across every
 * screen in the Stitch design. Kept deliberately dumb so callers decide
 * padding, shape, and layout.
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Spacing.cardPadding,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    // The press-scale must wrap the whole card (it sits before clip/background/border) so the
    // fill, outline, and content scale together as one unit; otherwise the painted background
    // would stay put while only the content scaled, mangling selection-colored cards. The
    // clickable/ripple sits after clip so the ripple stays bounded to the rounded shape.
    val base = modifier
        .then(if (onClick != null) Modifier.pressScale(pressed) else Modifier)
        .clip(shape)
        .background(backgroundColor)
        .border(BorderStroke(1.dp, borderColor), shape)
        .then(
            if (onClick != null)
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            else Modifier
        )
        .padding(contentPadding)
    Column(base, content = content)
}
