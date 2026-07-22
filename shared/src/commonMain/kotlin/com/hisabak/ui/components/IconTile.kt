package com.hisabak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hisabak.ui.icons.HugeIcons
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.Sizing
import com.hisabak.ui.theme.Spacing

/**
 * Rounded tile with a tinted background and a centered icon. Used for category
 * avatars, list-row leading icons, and small stat indicators.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Spacing.s9,
    iconSize: Dp = Sizing.iconSm,
    background: Color = Color.Unspecified,
    foreground: Color = Color.Unspecified,
    shape: Shape = RoundedCornerShape(10.dp),
    contentDescription: String? = null,
) {
    val c = HisabakTheme.colors
    val bg = if (background == Color.Unspecified) c.catGray.copy(alpha = 0.15f) else background
    val fg = if (foreground == Color.Unspecified) c.catGray else foreground
    Box(
        modifier = modifier
            .size(size)
            .background(bg, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = fg,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun CircleIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = Sizing.controlHeight,
    iconSize: Dp = Sizing.icon,
    background: Color = Color.Unspecified,
    foreground: Color = Color.Unspecified,
    contentDescription: String? = null,
) = IconTile(
    icon = icon,
    modifier = modifier,
    size = size,
    iconSize = iconSize,
    background = background,
    foreground = foreground,
    shape = CircleShape,
    contentDescription = contentDescription,
)

/** Maps the app's category color keys to (background, foreground) pairs. */
@Composable
fun tintPairForColor(key: String?): Pair<Color, Color> {
    val c = HisabakTheme.colors
    return when (key) {
        "green"  -> c.incomeSoft to c.income
        "blue"   -> c.savingsSoft to c.savings
        "orange" -> c.catOrange.copy(alpha = 0.15f) to c.catOrange
        "red"    -> c.expenseSoft to c.expense
        "teal"   -> c.catTeal.copy(alpha = 0.15f) to c.catTeal
        "purple" -> c.investmentSoft to c.investment
        "pink"   -> c.catPink.copy(alpha = 0.15f) to c.catPink
        else     -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** Maps category icon keys (persisted as short strings) to Hugeicons vectors. */
fun iconForKey(key: String?): ImageVector = when (key) {
    "wallet"    -> HugeIcons.Wallet
    "cart"      -> HugeIcons.Cart
    "briefcase" -> HugeIcons.Briefcase
    "car"       -> HugeIcons.Car
    "utensils"  -> HugeIcons.Utensils
    "piggy-bank"-> HugeIcons.PiggyBank
    "home"      -> HugeIcons.Home
    "film"      -> HugeIcons.Film
    "book"      -> HugeIcons.Book
    "heart"     -> HugeIcons.Heart
    "gift"      -> HugeIcons.Gift
    "plane"     -> HugeIcons.Plane
    else        -> HugeIcons.Circle
}
