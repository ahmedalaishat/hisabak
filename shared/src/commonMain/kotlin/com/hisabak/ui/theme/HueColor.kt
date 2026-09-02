package com.hisabak.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * Deriving a category color from a hue.
 *
 * The user picks a hue; the app picks the shade. That split is what keeps a custom color as
 * legible as a hand-tuned one: lightness and chroma are fixed per theme, so no hue can come out
 * too pale on the light surface or too dark on the dark one.
 *
 * The math is OKLCH, not HSL, because HSL's lightness is not perceptual — yellow and blue at the
 * same HSL lightness differ enormously in how bright they look, which is exactly the failure a
 * fixed-lightness rule has to avoid.
 */

/** Foreground (stroke/text) shade for a hue on the light theme. */
fun hueForegroundLight(hue: Int): Color = oklch(L_FG_LIGHT, C_FG, hue.toFloat())

/** Foreground shade for a hue on the dark theme — lighter, so it lifts off a dark surface. */
fun hueForegroundDark(hue: Int): Color = oklch(L_FG_DARK, C_FG, hue.toFloat())

/** Opaque pale tint for a hue, used where alpha isn't available (notification tiles). */
fun hueTintOpaque(hue: Int): Color = oklch(L_TINT, C_TINT, hue.toFloat())

private const val L_FG_LIGHT = 0.55f
private const val L_FG_DARK = 0.78f
private const val C_FG = 0.15f
private const val L_TINT = 0.94f
private const val C_TINT = 0.05f

/**
 * OKLCH -> sRGB. Out-of-gamut results are pulled back by dropping chroma rather than clipping
 * channels, which would shift the hue the user chose.
 */
fun oklch(lightness: Float, chroma: Float, hueDegrees: Float): Color {
    var c = chroma
    while (c > 0f) {
        val rgb = oklabToLinearSrgb(lightness, c, hueDegrees)
        if (rgb.all { it >= -1e-4f && it <= 1f + 1e-4f }) {
            return Color(
                red = gamma(rgb[0]),
                green = gamma(rgb[1]),
                blue = gamma(rgb[2]),
            )
        }
        c -= 0.005f
    }
    val gray = gamma(lightness)
    return Color(gray, gray, gray)
}

private fun oklabToLinearSrgb(lightness: Float, chroma: Float, hueDegrees: Float): FloatArray {
    val h = hueDegrees * PI_OVER_180
    val a = chroma * cos(h)
    val b = chroma * sin(h)

    val lp = lightness + 0.3963377774f * a + 0.2158037573f * b
    val mp = lightness - 0.1055613458f * a - 0.0638541728f * b
    val sp = lightness - 0.0894841775f * a - 1.2914855480f * b

    val l = lp * lp * lp
    val m = mp * mp * mp
    val s = sp * sp * sp

    return floatArrayOf(
        4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s,
        -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s,
        -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s,
    )
}

private fun gamma(linear: Float): Float {
    val x = linear.coerceIn(0f, 1f)
    return if (x <= 0.0031308f) 12.92f * x else 1.055f * x.pow(1f / 2.4f) - 0.055f
}

private const val PI_OVER_180 = 0.017453292f
