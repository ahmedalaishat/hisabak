package com.hisabak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hisabak.R
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing
import com.hisabak.ui.theme.Tajawal
import java.util.Locale
import kotlin.math.abs

/*
 * Worked examples — these show how the HTML/React design-system primitives translate
 * to Composables against the Hisabak theme. They are a PATTERN to copy, not the full
 * component set. Match the originals' look (see the design system's component cards +
 * .prompt.md files) but use your app's existing Composables where they already exist.
 */

/**
 * DirhamGlyph — the AED currency mark (res/drawable/ic_dirham), sized to sit
 * inline with text and tinted to match. Use before a number instead of "AED".
 */
private const val DIRHAM_ASPECT = 1000f / 870f // symbol is wider than tall

@Composable
fun DirhamGlyph(
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    tint: Color = LocalContentColor.current,
) {
    val heightDp = with(LocalDensity.current) { size.toDp() }
    Icon(
        painter = painterResource(R.drawable.ic_dirham),
        contentDescription = stringResource(R.string.currency_dirham_description),
        tint = tint,
        modifier = modifier
            .height(heightDp)
            .width(heightDp * DIRHAM_ASPECT),
    )
}

/**
 * AmountText — money with tabular Geist Mono figures and signed coloring.
 * Renders the dirham glyph in place of a currency code. Income green, expense coral.
 *
 *   AmountText(value = 8200.0)            // +⊅ 8,200.00 (green)
 *   AmountText(value = -342.75)           // −⊅ 342.75 (coral)
 *   AmountText(value = 12450.0, tone = AmountTone.Neutral, showSign = false, size = 40.sp)
 */
enum class AmountTone { Auto, Income, Expense, Savings, Investment, Neutral }

@Composable
fun AmountText(
    value: Double,
    modifier: Modifier = Modifier,
    currency: String = "AED", // retained for API compatibility; AED renders as the dirham glyph
    showSign: Boolean = true,
    tone: AmountTone = AmountTone.Auto,
    size: TextUnit = 16.sp,
    weight: FontWeight = FontWeight.SemiBold,
) {
    val c = HisabakTheme.colors
    val resolved = when (tone) {
        AmountTone.Auto -> if (value < 0) AmountTone.Expense else AmountTone.Income
        else -> tone
    }
    val color = when (resolved) {
        AmountTone.Income -> c.income
        AmountTone.Expense -> c.expense
        AmountTone.Savings -> c.savings
        AmountTone.Investment -> c.investment
        else -> MaterialTheme.colorScheme.onSurface
    }
    // Sign follows the resolved tone (income/savings/investment → +, expense → −) rather than the
    // raw value sign, so callers that pass an absolute value with an explicit tone (e.g. the
    // transaction list and SMS inbox) still render the correct − for expenses.
    val sign = if (showSign && tone != AmountTone.Neutral) (if (resolved == AmountTone.Expense) "−" else "+") else ""
    // Number and suffix are separate Texts so Arabic-Indic digits don't bidi-reorder; the Row
    // follows the ambient layout direction, so the dirham glyph falls on the natural side (left in
    // English, right in Arabic).
    val arabic = rememberIsArabic()
    // Geist Mono lacks Arabic-Indic glyphs — render Arabic figures in the Arabic UI face (Tajawal).
    val numberStyle = HisabakType.amount.copy(
        fontSize = size,
        fontWeight = weight,
        fontFamily = if (arabic) Tajawal else HisabakType.amount.fontFamily,
    )
    val parts = compactAmountParts(abs(value), arabic)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (sign.isNotEmpty()) Text(sign, color = color, style = numberStyle)
        DirhamGlyph(size = size * 0.82f, tint = color)
        Spacer(Modifier.width(3.dp))
        Text(parts.number, color = color, style = numberStyle, maxLines = 1)
        if (parts.suffix.isNotEmpty()) {
            if (arabic) Spacer(Modifier.width(2.dp))
            Text(parts.suffix, color = color, style = numberStyle, maxLines = 1)
        }
    }
}

/**
 * MoneyText — dirham glyph + grouped amount (no decimals, "M" for millions),
 * for headline/summary figures that take a [Money]'s minor units.
 */
@Composable
fun MoneyText(
    amountMinor: Long,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    symbolScale: Float = 0.8f,
) {
    val arabic = rememberIsArabic()
    val parts = compactAmountParts(amountMinor / 100.0, arabic)
    // Geist Mono has no Arabic-Indic glyphs, so Arabic figures fall back to the system font. Render
    // them in Tajawal (the Arabic UI face) instead, keeping tabular alignment and a consistent look.
    val figureStyle = if (arabic) style.copy(fontFamily = Tajawal) else style
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        DirhamGlyph(size = style.fontSize * symbolScale, tint = color)
        Spacer(Modifier.width(3.dp))
        Text(parts.number, style = figureStyle, color = color, maxLines = 1)
        if (parts.suffix.isNotEmpty()) {
            if (arabic) Spacer(Modifier.width(2.dp))
            Text(parts.suffix, style = figureStyle, color = color, maxLines = 1)
        }
    }
}

/**
 * Compact money: thousands as `K`, millions as `M` (both to 2 decimals); under 1,000 exact to
 * 2 decimals. Used app-wide via [MoneyText] / [AmountText] and the per-screen formatters.
 *
 * The suffix is localized off the current default locale (Arabic uses the words ألف / مليون) —
 * the locale is set by `AppLocale.wrap`, so this stays correct in non-composable callers too.
 * Digits stay Western and amounts keep the dirham glyph in both languages.
 */
/** The number and (possibly empty) magnitude suffix of a compact amount, kept apart so the
 *  composables can render them as separate Texts — Arabic-Indic digits (bidi class AN) plus an
 *  Arabic letter suffix would otherwise reorder inside one Text, flipping the visual order. */
internal class CompactParts(val number: String, val suffix: String)

internal fun compactAmountParts(major: Double, arabic: Boolean): CompactParts {
    val a = abs(major)
    // Arabic uses Arabic-Indic digits (٠١٢…) and the one-letter abbreviations أ (ألف) / م (مليون),
    // which fit the same footprint as K/M (the full words overflow). The number locale is pinned to
    // [arabic] (from the Compose config), not the JVM default, so the digit script can't drift after
    // a language switch; English pins to US so digits/separators stay Western on any device.
    val loc = if (arabic) ARABIC_NUMBER_LOCALE else Locale.US
    return when {
        a >= 1_000_000 -> CompactParts("%,.2f".format(loc, major / 1_000_000.0), if (arabic) "م" else "M")
        a >= 1_000 -> CompactParts("%,.2f".format(loc, major / 1_000.0), if (arabic) "أ" else "K")
        else -> CompactParts("%,.2f".format(loc, major), "")
    }
}

internal fun compactAmount(
    major: Double,
    arabic: Boolean = Locale.getDefault().language == "ar",
): String {
    val p = compactAmountParts(major, arabic)
    return when {
        p.suffix.isEmpty() -> p.number
        arabic -> "${p.number} ${p.suffix}"
        else -> "${p.number}${p.suffix}"
    }
}

/** Arabic locale pinned to Arabic-Indic numerals (nu-arab) so amount digits are deterministic. */
private val ARABIC_NUMBER_LOCALE: Locale = Locale.forLanguageTag("ar-u-nu-arab")

internal fun compactAmountMinor(
    amountMinor: Long,
    arabic: Boolean = Locale.getDefault().language == "ar",
): String = compactAmount(amountMinor / 100.0, arabic)

/** True when the UI is rendering in Arabic — read from the Compose config, not the JVM default. */
@Composable
internal fun rememberIsArabic(): Boolean =
    androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language == "ar"

private val ARABIC_INDIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

/** Maps Western digits to Arabic-Indic when [arabic], for numbers built with a fixed (non-locale)
 *  formatter (percentages, etc.) so they match the rest of the Arabic UI regardless of device. */
internal fun localizeDigits(text: String, arabic: Boolean): String {
    if (!arabic) return text
    return buildString(text.length) {
        for (ch in text) append(if (ch in '0'..'9') ARABIC_INDIC_DIGITS[ch - '0'] else ch)
    }
}

/**
 * StatusChip — SMS parse state. Mirrors components/core/StatusChip.
 * linked → green, parsed → blue (info), unparsed → gray.
 */
enum class SmsStatus { Linked, Parsed, Unparsed }

@Composable
fun StatusChip(status: SmsStatus, modifier: Modifier = Modifier) {
    val c = HisabakTheme.colors
    val (label, bg, fg) = when (status) {
        SmsStatus.Linked   -> Triple("Linked", c.incomeSoft, c.income)
        SmsStatus.Parsed   -> Triple("Parsed", c.infoSoft, c.info)
        SmsStatus.Unparsed -> Triple("Unparsed", c.surfaceSunken, c.textTertiary)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .background(bg, PillShape)
            .padding(horizontal = 10.dp, vertical = Spacing.s2),
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}
