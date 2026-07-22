package com.hisabak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/*
 * HisabakTheme — wraps MaterialTheme with the Hisabak color scheme, typography, and
 * shapes, and provides the finance-specific colors via LocalHisabakColors.
 *
 * Usage:
 *   setContent { HisabakTheme { AppNavHost() } }
 *
 * Read finance colors anywhere under it:
 *   val c = HisabakTheme.colors
 *   Text(money, color = c.income, style = HisabakType.amount)
 *
 * Standard Material colors still come from MaterialTheme.colorScheme.primary etc.
 */

val LocalHisabakColors = staticCompositionLocalOf { HisabakLightSemantic }

@Composable
fun HisabakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) HisabakDarkColorScheme else HisabakLightColorScheme
    val semantic = if (darkTheme) HisabakDarkSemantic else HisabakLightSemantic

    // Arabic needs the tracking-cleared typography so connected glyphs don't mis-measure and wrap.
    val isArabic = Locale.current.language == "ar"
    val fonts = HisabakFontFamilies(
        sans = dmSansFamily(),
        mono = geistMonoFamily(),
        arabic = tajawalFamily(),
    )
    val typography = hisabakTypography(if (isArabic) fonts.arabic else fonts.sans, isArabic)

    CompositionLocalProvider(
        LocalHisabakColors provides semantic,
        LocalHisabakFonts provides fonts,
        LocalReducedMotion provides rememberReducedMotion(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = HisabakShapes,
            content = content,
        )
    }
}

/** Accessor object — mirrors the MaterialTheme pattern: `HisabakTheme.colors.income`. */
object HisabakTheme {
    val colors: HisabakSemanticColors
        @Composable @ReadOnlyComposable get() = LocalHisabakColors.current
}
