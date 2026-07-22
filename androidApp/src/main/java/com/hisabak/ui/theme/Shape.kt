package com.hisabak.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/*
 * Hisabak shapes & spacing — generated from tokens/spacing.css.
 * 8dp base grid. 16dp page margin. 12dp default card radius.
 */

val HisabakShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),   // --r-xs
    small      = RoundedCornerShape(8.dp),   // --r-sm
    medium     = RoundedCornerShape(12.dp),  // --r-md  (default card)
    large      = RoundedCornerShape(16.dp),  // --r-lg  (hero card / sheet)
    extraLarge = RoundedCornerShape(24.dp),  // --r-xl  (sheet top)
)

/** Pill for buttons/chips/badges; tile for category icons. */
val PillShape = RoundedCornerShape(percent = 50)
val TileShape = RoundedCornerShape(14.dp)    // --r-tile

/** Spacing scale + semantic spacing (dp). Use instead of magic numbers. */
object Spacing {
    val s1 = 2.dp
    val s2 = 4.dp
    val s3 = 8.dp
    val s4 = 12.dp
    val s5 = 16.dp
    val s6 = 20.dp
    val s7 = 24.dp
    val s8 = 32.dp
    val s9 = 40.dp
    val s10 = 48.dp

    // semantic
    val pageMargin = 16.dp
    val cardPadding = 16.dp
    val cardGap = 12.dp
    val sectionGap = 24.dp
    val sectionTitleGap = 8.dp
}

/** Component sizing. */
object Sizing {
    val controlHeight = 48.dp     // buttons / inputs
    val controlHeightSm = 36.dp   // chips / small buttons
    val tileSize = 44.dp          // category icon tile
    val avatar = 36.dp
    val tapMin = 44.dp            // minimum touch target
    val icon = 24.dp
    val iconSm = 20.dp
    val appBarHeight = 56.dp
    val bottomNavHeight = 64.dp
}
