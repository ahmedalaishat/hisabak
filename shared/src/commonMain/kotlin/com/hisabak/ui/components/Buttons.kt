package com.hisabak.ui.components

import com.hisabak.ui.icons.HugeIcons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

enum class ButtonVariant { Primary, Secondary, Ghost, Danger }

@Composable
fun HisabakButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    fullWidth: Boolean = false,
) {
    val c = HisabakTheme.colors
    val cs = MaterialTheme.colorScheme

    val bg = when (variant) {
        ButtonVariant.Primary   -> cs.primary
        ButtonVariant.Secondary -> Color.Transparent
        ButtonVariant.Ghost     -> Color.Transparent
        ButtonVariant.Danger    -> c.expenseSoft
    }
    val fg = when (variant) {
        ButtonVariant.Primary   -> cs.onPrimary
        ButtonVariant.Secondary -> cs.onSurface
        ButtonVariant.Ghost     -> cs.primary
        ButtonVariant.Danger    -> c.expense
    }
    val borderColor = when (variant) {
        ButtonVariant.Secondary -> cs.outlineVariant
        else                    -> Color.Transparent
    }

    val widthModifier = if (fullWidth) modifier.fillMaxWidth() else modifier
    val enabledAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.45f,
        label = "buttonAlpha",
    )
    val alphaModifier = widthModifier.alpha(enabledAlpha)

    Row(
        modifier = alphaModifier
            .height(Sizing.controlHeight)
            .hisabakPressable(
                shape = PillShape,
                background = bg,
                border = if (variant == ButtonVariant.Secondary)
                    BorderStroke(1.dp, borderColor) else null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.s6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterHorizontally),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(Sizing.iconSm),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}

/**
 * Green pill button used for primary actions in the design (Create Brand,
 * Create Category, + New). Default rounded corners are 12dp to match the
 * rest of the UI (cards, chips).
 */
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    horizontal: Dp = Spacing.cardPadding,
    vertical: Dp = Spacing.s4,
) {
    Row(
        modifier = modifier
            .hisabakPressable(
                shape = MaterialTheme.shapes.medium,
                background = containerColor,
                onClick = onClick,
            )
            .padding(PaddingValues(horizontal = horizontal, vertical = vertical)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun CreateActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) = PrimaryPillButton(
    text = text,
    onClick = onClick,
    modifier = modifier,
    leadingIcon = if (showIcon) HugeIcons.Add else null,
)
