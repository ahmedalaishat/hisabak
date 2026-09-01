package com.hisabak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.hisabak.shared.resources.Res
import com.hisabak.shared.resources.amount_show_exact
import com.hisabak.shared.resources.currency_dirham_description
import com.hisabak.shared.resources.ic_dirham
import com.hisabak.ui.theme.HisabakTheme
import com.hisabak.ui.theme.HisabakType
import com.hisabak.ui.theme.LocalHisabakFonts
import com.hisabak.ui.theme.PillShape
import com.hisabak.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.round
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
        painter = painterResource(Res.drawable.ic_dirham),
        contentDescription = stringResource(Res.string.currency_dirham_description),
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
    // Sign: expenses always −; other tones follow the value's sign so a signed value (e.g. a
    // savings withdrawal) reads − while callers that pass absolute values keep their +. Neutral
    // shows − for negatives only, never a +.
    val sign = when {
        !showSign -> ""
        resolved == AmountTone.Expense || value < 0 -> "−"
        resolved == AmountTone.Neutral -> ""
        else -> "+"
    }
    // Number and suffix are separate Texts so Arabic-Indic digits don't bidi-reorder; the Row
    // follows the ambient layout direction, so the dirham glyph falls on the natural side (left in
    // English, right in Arabic).
    val arabic = rememberIsArabic()
    // Geist Mono lacks Arabic-Indic glyphs — render Arabic figures in the Arabic UI face (Tajawal).
    val amountStyle = HisabakType.amount
    val numberStyle = amountStyle.copy(
        fontSize = size,
        fontWeight = weight,
        fontFamily = if (arabic) LocalHisabakFonts.current.arabic else amountStyle.fontFamily,
    )
    val parts = compactAmountParts(abs(value), arabic)
    val shown = rememberRevealableAmount(parts, abs(value), arabic)
    Row(
        modifier = modifier.revealOnTap(shown).speakExactly(sign, shown.exact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sign.isNotEmpty()) Text(sign, color = color, style = numberStyle)
        DirhamGlyph(size = size * 0.82f, tint = color)
        Spacer(Modifier.width(3.dp))
        Text(
            shown.number,
            color = color,
            style = numberStyle,
            maxLines = 1,
            autoSize = if (shown.revealed) revealAutoSize(size) else null,
        )
        if (shown.suffix.isNotEmpty()) {
            if (arabic) Spacer(Modifier.width(2.dp))
            Text(shown.suffix, color = color, style = numberStyle, maxLines = 1)
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
    val major = abs(amountMinor / 100.0)
    val parts = compactAmountParts(major, arabic)
    val shown = rememberRevealableAmount(parts, major, arabic)
    // Geist Mono has no Arabic-Indic glyphs, so Arabic figures fall back to the system font. Render
    // them in Tajawal (the Arabic UI face) instead, keeping tabular alignment and a consistent look.
    val figureStyle = if (arabic) style.copy(fontFamily = LocalHisabakFonts.current.arabic) else style
    val sign = if (amountMinor < 0) "−" else ""
    Row(
        modifier = modifier.revealOnTap(shown).speakExactly(sign, shown.exact),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // True minus before the glyph, matching AmountText — a bucket that nets negative (more
        // withdrawn than deposited) must not render the sign inside the figures after the glyph.
        if (sign.isNotEmpty()) Text(sign, style = figureStyle, color = color)
        DirhamGlyph(size = style.fontSize * symbolScale, tint = color)
        Spacer(Modifier.width(3.dp))
        Text(
            shown.number,
            style = figureStyle,
            color = color,
            maxLines = 1,
            autoSize = if (shown.revealed) revealAutoSize(figureStyle.fontSize) else null,
        )
        if (shown.suffix.isNotEmpty()) {
            if (arabic) Spacer(Modifier.width(2.dp))
            Text(shown.suffix, style = figureStyle, color = color, maxLines = 1)
        }
    }
}

/**
 * Compact money: thousands as `K`, millions as `M` (both to 2 decimals); under 1,000 exact to
 * 2 decimals. Used app-wide via [MoneyText] / [AmountText] and the per-screen formatters.
 * Amounts read as columns of short, aligned figures; the exact value is a tap away wherever
 * something was actually abbreviated (see [rememberRevealableAmount]).
 *
 * The suffix is localized off the current default locale (Arabic uses the words ألف / مليون) —
 * the locale is set by `AppLocale.wrap`, so this stays correct in non-composable callers too.
 * Digits stay Western and amounts keep the dirham glyph in both languages.
 */
/** The number and (possibly empty) magnitude suffix of a compact amount, kept apart so the
 *  composables can render them as separate Texts — Arabic-Indic digits (bidi class AN) plus an
 *  Arabic letter suffix would otherwise reorder inside one Text, flipping the visual order. */
class CompactParts(val number: String, val suffix: String)

fun compactAmountParts(major: Double, arabic: Boolean): CompactParts {
    val a = abs(major)
    // Arabic uses Arabic-Indic digits (٠١٢…) and the one-letter abbreviations أ (ألف) / م (مليون),
    // which fit the same footprint as K/M (the full words overflow). The digit script is pinned to
    // [arabic] (from the Compose locale), so it can't drift after a language switch; English keeps
    // Western digits/separators on any device.
    return when {
        a >= 1_000_000 -> CompactParts(formatGrouped2(major / 1_000_000.0, arabic), if (arabic) "م" else "M")
        a >= 1_000 -> CompactParts(formatGrouped2(major / 1_000.0, arabic), if (arabic) "أ" else "K")
        else -> CompactParts(formatGrouped2(major, arabic), "")
    }
}

fun compactAmount(
    major: Double,
    arabic: Boolean = Locale.current.language == "ar",
): String {
    val p = compactAmountParts(major, arabic)
    return when {
        p.suffix.isEmpty() -> p.number
        arabic -> "${p.number} ${p.suffix}"
        else -> "${p.number}${p.suffix}"
    }
}

fun compactAmountMinor(
    amountMinor: Long,
    arabic: Boolean = Locale.current.language == "ar",
): String = compactAmount(amountMinor / 100.0, arabic)

/** The full grouped figure with no magnitude suffix — what an abbreviated amount reveals. */
fun exactAmount(major: Double, arabic: Boolean): String = formatGrouped2(major, arabic)

/*
 * Tap to reveal the exact amount.
 *
 * An abbreviated figure hides digits (⊅ 1.25M is really ⊅ 1,248,300.50), so every amount that
 * actually lost something is tappable and swaps to the full figure in place, reverting after a
 * few seconds. Amounts that were never abbreviated stay inert — no affordance without payoff.
 * Only one amount is expanded at a time ([LocalRevealedAmount] holds its token), so revealing a
 * second collapses the first instead of widening two rows at once.
 */
private const val REVEAL_TIMEOUT_MS = 4_000L

/** The currently revealed amount's token. [com.hisabak.ui.theme.HisabakTheme] provides it; with
 *  no provider (previews) each amount falls back to revealing on its own. */
val LocalRevealedAmount = staticCompositionLocalOf<MutableState<Any?>?> { null }

internal class RevealableAmount(
    val number: String,
    val suffix: String,
    val exact: String,
    val revealed: Boolean,
    val canReveal: Boolean,
    val onToggle: () -> Unit,
)

@Composable
internal fun rememberRevealableAmount(
    parts: CompactParts,
    major: Double,
    arabic: Boolean,
): RevealableAmount {
    val canReveal = parts.suffix.isNotEmpty()
    val exact = if (canReveal) exactAmount(major, arabic) else parts.number
    val token = remember { Any() }
    val fallback = remember { mutableStateOf<Any?>(null) }
    val revealedToken = LocalRevealedAmount.current ?: fallback
    val revealed = canReveal && revealedToken.value === token
    LaunchedEffect(revealed) {
        if (revealed) {
            delay(REVEAL_TIMEOUT_MS)
            if (revealedToken.value === token) revealedToken.value = null
        }
    }
    return RevealableAmount(
        number = if (revealed) exact else parts.number,
        suffix = if (revealed) "" else parts.suffix,
        exact = exact,
        revealed = revealed,
        canReveal = canReveal,
        onToggle = { revealedToken.value = if (revealed) null else token },
    )
}

/** The exact figure is longer than the abbreviation it replaces, and its container (a hero row
 *  beside a trend badge, a third-width tile) does not grow — so the revealed figure shrinks to
 *  fit rather than losing its last digits. The glyph is already drawn at 0.8× the figures, so it
 *  stays put. */
private fun revealAutoSize(base: TextUnit): TextAutoSize? =
    if (base.isSpecified) {
        TextAutoSize.StepBased(minFontSize = base * 0.6f, maxFontSize = base, stepSize = 0.5.sp)
    } else {
        null
    }

@Composable
private fun Modifier.revealOnTap(amount: RevealableAmount): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val label = stringResource(Res.string.amount_show_exact)
    if (!amount.canReveal) return this
    // No indication: a ripple over an inline figure reads as a button. The swap is the feedback.
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClickLabel = label,
        onClick = amount.onToggle,
    )
}

/** Screen readers always get the full figure, never the abbreviation — and the currency glyph
 *  reads once as its name instead of the icon plus two separate number Texts. */
@Composable
private fun Modifier.speakExactly(sign: String, exact: String): Modifier {
    val currency = stringResource(Res.string.currency_dirham_description)
    // U+2212 is the display minus; screen readers announce the ASCII hyphen reliably.
    val spoken = "${sign.replace('−', '-')}$exact $currency"
    return clearAndSetSemantics { contentDescription = spoken }
}

/* Arabic-Indic separators (ICU's for ar): U+066C thousands, U+066B decimal. */
private const val ARABIC_GROUP_SEPARATOR = '٬'
private const val ARABIC_DECIMAL_SEPARATOR = '٫'

/**
 * Pure-Kotlin replacement for `"%,.2f".format(locale, value)`: two decimals (HALF_UP), grouped
 * thousands, and Arabic-Indic digits + separators when [arabic]. Rounds on the shortest decimal
 * representation ([Double.toString]) — the digits java.util.Formatter used — so output matches
 * the previous JVM formatting.
 */
private fun formatGrouped2(value: Double, arabic: Boolean): String {
    val (intDigits, fracDigits) = roundTo2(abs(value))
    val out = buildString {
        if (value < 0) append('-')
        val n = intDigits.length
        for (i in 0 until n) {
            append(intDigits[i])
            val remaining = n - 1 - i
            if (remaining > 0 && remaining % 3 == 0) append(if (arabic) ARABIC_GROUP_SEPARATOR else ',')
        }
        append(if (arabic) ARABIC_DECIMAL_SEPARATOR else '.')
        append(fracDigits)
    }
    return localizeDigits(out, arabic)
}

private fun roundTo2(value: Double): Pair<String, String> {
    val repr = value.toString()
    if ('E' in repr || 'e' in repr) {
        // Scientific notation only appears from ~1e7 up — beyond any real amount after the K/M
        // division — so plain cent rounding is fine here.
        val cents = round(value * 100).toLong()
        return (cents / 100).toString() to (cents % 100).toString().padStart(2, '0')
    }
    val dot = repr.indexOf('.')
    var intPart = if (dot < 0) repr else repr.substring(0, dot)
    val frac = if (dot < 0) "" else repr.substring(dot + 1)
    val fracPart = when {
        frac.length <= 2 -> frac.padEnd(2, '0')
        frac[2] >= '5' -> {
            val bumped = frac.substring(0, 2).toInt() + 1
            if (bumped == 100) {
                intPart = (intPart.toLong() + 1).toString()
                "00"
            } else {
                bumped.toString().padStart(2, '0')
            }
        }
        else -> frac.substring(0, 2)
    }
    return intPart to fracPart
}

/** True when the UI is rendering in Arabic — read from the Compose locale (which the in-app
 *  language switch also applies to the process default). */
@Composable
fun rememberIsArabic(): Boolean = Locale.current.language == "ar"

private val ARABIC_INDIC_DIGITS = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

/** Maps Western digits to Arabic-Indic when [arabic], for numbers built with a fixed (non-locale)
 *  formatter (percentages, etc.) so they match the rest of the Arabic UI regardless of device. */
fun localizeDigits(text: String, arabic: Boolean): String {
    if (!arabic) return text
    return buildString(text.length) {
        for (ch in text) append(if (ch in '0'..'9') ARABIC_INDIC_DIGITS[ch - '0'] else ch)
    }
}

/**
 * A numeric format arg for CMP `stringResource`/`pluralStringResource`. CMP does plain string
 * interpolation for `%1$d` (no locale-aware number formatting), so Arabic-Indic digits must be
 * baked into the argument itself.
 */
@Composable
fun localizedFormatArg(n: Int): String = localizeDigits(n.toString(), rememberIsArabic())

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
