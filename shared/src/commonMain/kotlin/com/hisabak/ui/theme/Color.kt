package com.hisabak.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * Hisabak color system — generated from the design-system tokens (tokens/colors.css).
 *
 * Two layers:
 *   1) Material 3 ColorScheme (HisabakLightColorScheme / HisabakDarkColorScheme) — the
 *      slots MaterialTheme + standard components read.
 *   2) HisabakSemanticColors — the finance-specific colors Material has no slot for
 *      (income/expense/savings/investment + the 8 category colors). Exposed via
 *      LocalHisabakColors (see HisabakTheme.kt) so you call HisabakTheme.colors.income.
 *
 * RULE: green (primary) is meaningful, never decorative — income, the single primary
 * action per screen, and the active nav tab. Backgrounds stay neutral.
 */

/* ---------------- Brand green ramp ---------------- */
val Green50  = Color(0xFFE7F5EF)
val Green100 = Color(0xFFC9E9DB)
val Green200 = Color(0xFF95D4BB)
val Green300 = Color(0xFF5DBC97)
val Green400 = Color(0xFF2A9E76)
val Green500 = Color(0xFF0B7A5B) // brand primary
val Green600 = Color(0xFF096A4F)
val Green700 = Color(0xFF08543F)
val Green800 = Color(0xFF0E3A2F)
val Green900 = Color(0xFF0A2A22)

/* ---------------- Material 3 schemes ---------------- */
val HisabakLightColorScheme = lightColorScheme(
    primary = Color(0xFF0B7A5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE7F5EF),
    onPrimaryContainer = Color(0xFF08543F),
    secondary = Color(0xFF4B5563),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEDEFF2),
    onSecondaryContainer = Color(0xFF1F2530),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFEDEFF2),      // = --surface-sunken
    onSurfaceVariant = Color(0xFF6B7280),    // = --text-secondary
    outline = Color(0xFFE5E7EB),             // = --border
    outlineVariant = Color(0xFFEDEFF2),      // = --divider
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCEAE9),
    onErrorContainer = Color(0xFF7A1B17),
    inverseSurface = Color(0xFF1F2530),
    inverseOnSurface = Color(0xFFF6F7F9),
    scrim = Color(0x73111827),               // --scrim, 45%
)

val HisabakDarkColorScheme = darkColorScheme(
    primary = Color(0xFF21A87C),
    onPrimary = Color(0xFF06120D),
    primaryContainer = Color(0xFF14342A),
    onPrimaryContainer = Color(0xFF95D4BB),
    secondary = Color(0xFF9BA3B0),
    onSecondary = Color(0xFF0B0D12),
    secondaryContainer = Color(0xFF1C212B),
    onSecondaryContainer = Color(0xFFF3F5F8),
    background = Color(0xFF0B0D12),
    onBackground = Color(0xFFF3F5F8),
    surface = Color(0xFF161A22),
    onSurface = Color(0xFFF3F5F8),
    surfaceVariant = Color(0xFF11141B),      // = --surface-sunken (dark)
    onSurfaceVariant = Color(0xFF9BA3B0),    // = --text-secondary (dark)
    outline = Color(0xFF262C38),             // = --border (dark)
    outlineVariant = Color(0xFF20252F),      // = --divider (dark)
    error = Color(0xFFF0726A),
    onError = Color(0xFF06120D),
    errorContainer = Color(0x24F0726A),
    onErrorContainer = Color(0xFFF0726A),
    inverseSurface = Color(0xFFF3F5F8),
    inverseOnSurface = Color(0xFF161A22),
    scrim = Color(0x99000000),               // --scrim (dark), 60%
)

/* ---------------- Finance + category extension ---------------- */
@Immutable
data class HisabakSemanticColors(
    // financial meanings
    val income: Color, val incomeSoft: Color,
    val expense: Color, val expenseSoft: Color,
    val savings: Color, val savingsSoft: Color,
    val investment: Color, val investmentSoft: Color,
    // feedback (beyond Material error)
    val warning: Color, val warningSoft: Color,
    val info: Color, val infoSoft: Color,
    // neutral helpers not in the M3 scheme
    val textTertiary: Color,
    val surfaceSunken: Color,
    val borderStrong: Color,
    val accentHover: Color,
    val accentPressed: Color,
    // 8 category swatches (the color picker)
    val catGreen: Color, val catBlue: Color, val catOrange: Color, val catRed: Color,
    val catTeal: Color, val catPurple: Color, val catPink: Color, val catGray: Color,
)

val HisabakLightSemantic = HisabakSemanticColors(
    income = Color(0xFF0B7A5B), incomeSoft = Color(0xFFE7F5EF),
    expense = Color(0xFFE5544B), expenseSoft = Color(0xFFFCEAE9),
    savings = Color(0xFF2F6FED), savingsSoft = Color(0xFFE6EEFD),
    investment = Color(0xFF7C5CFC), investmentSoft = Color(0xFFECE7FE),
    warning = Color(0xFFC98A14), warningSoft = Color(0xFFFBF1DC),
    info = Color(0xFF2F6FED), infoSoft = Color(0xFFE6EEFD),
    textTertiary = Color(0xFF9CA3AF),
    surfaceSunken = Color(0xFFEDEFF2),
    borderStrong = Color(0xFFD3D7DE),
    accentHover = Color(0xFF096A4F),
    accentPressed = Color(0xFF08543F),
    catGreen = Color(0xFF0B7A5B), catBlue = Color(0xFF2F6FED), catOrange = Color(0xFFE8842B),
    catRed = Color(0xFFE5544B), catTeal = Color(0xFF138D90), catPurple = Color(0xFF7C5CFC),
    catPink = Color(0xFFDB4C8A), catGray = Color(0xFF6B7280),
)

val HisabakDarkSemantic = HisabakSemanticColors(
    income = Color(0xFF2DBC8C), incomeSoft = Color(0x242DBC8C),
    expense = Color(0xFFF0726A), expenseSoft = Color(0x24F0726A),
    savings = Color(0xFF5B8DF5), savingsSoft = Color(0x245B8DF5),
    investment = Color(0xFF9C82FF), investmentSoft = Color(0x249C82FF),
    warning = Color(0xFFE0A53A), warningSoft = Color(0x24E0A53A),
    info = Color(0xFF5B8DF5), infoSoft = Color(0x245B8DF5),
    textTertiary = Color(0xFF6B7280),
    surfaceSunken = Color(0xFF11141B),
    borderStrong = Color(0xFF333B49),
    accentHover = Color(0xFF2DBC8C),
    accentPressed = Color(0xFF1B8C68),
    catGreen = Color(0xFF2DBC8C), catBlue = Color(0xFF5B8DF5), catOrange = Color(0xFFF0A05A),
    catRed = Color(0xFFF0726A), catTeal = Color(0xFF36B5B8), catPurple = Color(0xFF9C82FF),
    catPink = Color(0xFFEC6FA6), catGray = Color(0xFF9BA3B0),
)
