package com.hisabak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.dm_sans_bold
import com.hisabak.shared.resources.dm_sans_medium
import com.hisabak.shared.resources.dm_sans_regular
import com.hisabak.shared.resources.dm_sans_semibold
import com.hisabak.shared.resources.geist_mono_medium
import com.hisabak.shared.resources.geist_mono_regular
import com.hisabak.shared.resources.geist_mono_semibold
import com.hisabak.shared.resources.tajawal_bold
import com.hisabak.shared.resources.tajawal_medium
import com.hisabak.shared.resources.tajawal_regular
import org.jetbrains.compose.resources.Font

/*
 * Hisabak typography — generated from tokens/typography.css.
 *   UI:   DM Sans   (geometric, calm)
 *   Money/codes: Geist Mono (tabular figures so amounts align in lists)
 *
 * Fonts are OFL TTFs bundled in shared/src/commonMain/composeResources/font/ (static
 * instances served by Google Fonts; see docs/kmp-migration.md for the source URLs) and
 * loaded through Compose Multiplatform resources — no downloadable-fonts provider.
 */

@Composable
fun dmSansFamily(): FontFamily = FontFamily(
    Font(Res.font.dm_sans_regular, FontWeight.Normal),
    Font(Res.font.dm_sans_medium, FontWeight.Medium),
    Font(Res.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(Res.font.dm_sans_bold, FontWeight.Bold),
)

/* Geist Mono deliberately tops out at SemiBold — Bold amount styles nearest-match to it,
 * matching how the downloadable-font family rendered before fonts were bundled. */
@Composable
fun geistMonoFamily(): FontFamily = FontFamily(
    Font(Res.font.geist_mono_regular, FontWeight.Normal),
    Font(Res.font.geist_mono_medium, FontWeight.Medium),
    Font(Res.font.geist_mono_semibold, FontWeight.SemiBold),
)

/*
 * Arabic UI face. Tajawal ships Latin + Arabic in one family and pairs cleanly with DM Sans's
 * geometry. It has no SemiBold (600), so Compose nearest-matches 600 → Bold (700), which reads
 * well for Arabic headings. Selected by locale in HisabakTheme.
 */
@Composable
fun tajawalFamily(): FontFamily = FontFamily(
    Font(Res.font.tajawal_regular, FontWeight.Normal),
    Font(Res.font.tajawal_medium, FontWeight.Medium),
    Font(Res.font.tajawal_bold, FontWeight.Bold),
)

/** The three Hisabak font families, resolved once per composition by [HisabakTheme]. */
class HisabakFontFamilies(
    val sans: FontFamily,
    val mono: FontFamily,
    val arabic: FontFamily,
)

/* Sensible fallbacks so components render outside HisabakTheme (previews/tests). */
val LocalHisabakFonts = staticCompositionLocalOf {
    HisabakFontFamilies(FontFamily.SansSerif, FontFamily.Monospace, FontFamily.SansSerif)
}

private val Tight = (-0.02).em

/*
 * Material 3 Typography — maps the Hisabak scale onto the slots components read. [family] is
 * DM Sans for Latin and Tajawal for Arabic. Arabic clears the Latin "tight" negative tracking
 * (and the overline's positive tracking): the connected, cursive script mis-measures under that
 * tracking, inflating text width so short title/headline words wrap onto a second line.
 */
internal fun hisabakTypography(family: FontFamily, arabic: Boolean): Typography {
    val tight = if (arabic) TextUnit.Unspecified else Tight
    val overline = if (arabic) TextUnit.Unspecified else 0.04.em
    return Typography(
        // Display (non-money hero text)
        displayMedium  = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = tight),
        displaySmall   = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = tight),
        // Headlines
        headlineLarge  = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = tight),
        headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = tight),
        headlineSmall  = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = tight),
        // Page title (24/600)
        titleLarge     = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = tight),
        // Section header (18/600)
        titleMedium    = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
        // List-row title (16/500)
        titleSmall     = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp),
        // Body (16/400)
        bodyLarge      = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
        // Body secondary (14/400) — used by OutlinedTextField hint, DropdownMenu, etc.
        bodyMedium     = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        // Caption / secondary (13/400)
        bodySmall      = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        // Button / chip / tab label (14/500)
        labelLarge     = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
        labelMedium    = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        // Overline (11/600, tracked, uppercase applied at call site)
        labelSmall     = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = overline),
    )
}

/*
 * Money styles — Geist Mono with tabular figures. Material has no slot for these;
 * use HisabakType.amount / amountHero directly on amount Text composables.
 * Composable getters: the mono family comes from bundled resources via
 * [LocalHisabakFonts] (set by HisabakTheme), so the styles can't be plain constants.
 */
object HisabakType {
    val amount: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalHisabakFonts.current.mono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
            lineHeight = 20.sp, fontFeatureSettings = "tnum",
        )
    val amountLarge: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalHisabakFonts.current.mono, fontWeight = FontWeight.Bold, fontSize = 22.sp,
            lineHeight = 26.sp, fontFeatureSettings = "tnum",
        )
    val amountHero: TextStyle
        @Composable get() = TextStyle(
            fontFamily = LocalHisabakFonts.current.mono, fontWeight = FontWeight.Bold, fontSize = 40.sp,
            lineHeight = 44.sp, fontFeatureSettings = "tnum",
        )
}
