package com.hisabak.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hisabak.R

/*
 * Hisabak typography — generated from tokens/typography.css.
 *   UI:   DM Sans   (geometric, calm)
 *   Money/codes: Geist Mono (tabular figures so amounts align in lists)
 *
 * Fonts are pulled via the Downloadable Fonts (Google Fonts) provider so you don't
 * bundle binaries. Requires:
 *   implementation("androidx.compose.ui:ui-text-google-fonts:<version>")
 * and the certs array `com_google_android_gms_fonts_certs` in res/values/font_certs.xml
 * (standard Google Fonts setup). If you'd rather bundle .ttf files, replace the
 * FontFamily definitions with res/font references — the weights are the same.
 */

private val googleProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val DMSansGF = GoogleFont("DM Sans")
private val GeistMonoGF = GoogleFont("Geist Mono")
private val TajawalGF = GoogleFont("Tajawal")

val DMSans = FontFamily(
    Font(DMSansGF, googleProvider, FontWeight.Normal),
    Font(DMSansGF, googleProvider, FontWeight.Medium),
    Font(DMSansGF, googleProvider, FontWeight.SemiBold),
    Font(DMSansGF, googleProvider, FontWeight.Bold),
)

val GeistMono = FontFamily(
    Font(GeistMonoGF, googleProvider, FontWeight.Normal),
    Font(GeistMonoGF, googleProvider, FontWeight.Medium),
    Font(GeistMonoGF, googleProvider, FontWeight.SemiBold),
)

/*
 * Arabic UI face. Tajawal ships Latin + Arabic in one family and pairs cleanly with DM Sans's
 * geometry. It has no SemiBold (600), so Compose nearest-matches 600 → Bold (700), which reads
 * well for Arabic headings. Used by [HisabakTypographyArabic], selected by locale in HisabakTheme.
 */
val Tajawal = FontFamily(
    Font(TajawalGF, googleProvider, FontWeight.Normal),
    Font(TajawalGF, googleProvider, FontWeight.Medium),
    Font(TajawalGF, googleProvider, FontWeight.Bold),
)

private val Tight = (-0.02).em

/*
 * Material 3 Typography — maps the Hisabak scale onto the slots components read. [family] is
 * DM Sans for Latin and Tajawal for Arabic. Arabic clears the Latin "tight" negative tracking
 * (and the overline's positive tracking): the connected, cursive script mis-measures under that
 * tracking, inflating text width so short title/headline words wrap onto a second line.
 */
private fun hisabakTypography(family: FontFamily, arabic: Boolean): Typography {
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

val HisabakTypography: Typography = hisabakTypography(DMSans, arabic = false)
val HisabakTypographyArabic: Typography = hisabakTypography(Tajawal, arabic = true)

/*
 * Money styles — Geist Mono with tabular figures. Material has no slot for these;
 * use HisabakType.amount / amountHero directly on amount Text composables.
 */
object HisabakType {
    val amount = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        lineHeight = 20.sp, fontFeatureSettings = "tnum",
    )
    val amountLarge = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 22.sp,
        lineHeight = 26.sp, fontFeatureSettings = "tnum",
    )
    val amountHero = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = 40.sp,
        lineHeight = 44.sp, fontFeatureSettings = "tnum",
    )
}
