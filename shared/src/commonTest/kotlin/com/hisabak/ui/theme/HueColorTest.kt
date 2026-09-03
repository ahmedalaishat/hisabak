package com.hisabak.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class HueColorTest {

    private val everyHue = (0 until 360 step 5).toList()

    /** Relative luminance (WCAG), for checking a derived color actually reads on its surface. */
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun `every hue produces an in-range color`() {
        everyHue.forEach { hue ->
            listOf(hueForegroundLight(hue), hueForegroundDark(hue), hueTintOpaque(hue)).forEach { c ->
                assertTrue(c.red in 0f..1f, "hue $hue red out of range: ${c.red}")
                assertTrue(c.green in 0f..1f, "hue $hue green out of range: ${c.green}")
                assertTrue(c.blue in 0f..1f, "hue $hue blue out of range: ${c.blue}")
            }
        }
    }

    @Test
    fun `light foregrounds stay dark enough to read on a light surface`() {
        val lightSurface = Color(0xFFFFFFFF)
        everyHue.forEach { hue ->
            val c = contrast(hueForegroundLight(hue), lightSurface)
            assertTrue(c >= 3.0, "hue $hue only reaches ${c}:1 on white")
        }
    }

    @Test
    fun `dark foregrounds stay light enough to read on a dark surface`() {
        val darkSurface = Color(0xFF14171C)
        everyHue.forEach { hue ->
            val c = contrast(hueForegroundDark(hue), darkSurface)
            assertTrue(c >= 3.0, "hue $hue only reaches ${c}:1 on the dark surface")
        }
    }

    @Test
    fun `the dark shade is always lighter than the light shade`() {
        everyHue.forEach { hue ->
            assertTrue(
                luminance(hueForegroundDark(hue)) > luminance(hueForegroundLight(hue)),
                "hue $hue does not lift in dark mode",
            )
        }
    }

    @Test
    fun `perceived lightness barely varies across hues`() {
        // The point of OKLCH over HSL: yellow must not come out far brighter than blue.
        val lums = everyHue.map { luminance(hueForegroundLight(it)) }
        val spread = (lums.max() - lums.min()) / lums.max()
        assertTrue(spread < 0.75, "light shades vary too much across hues: spread $spread")
    }

    @Test
    fun `neighbouring hues stay distinguishable`() {
        everyHue.forEach { hue ->
            val a = hueForegroundLight(hue)
            val b = hueForegroundLight((hue + 30) % 360)
            val delta = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
            assertTrue(delta > 0.05f, "hue $hue and ${hue + 30} render too alike (delta $delta)")
        }
    }

    @Test
    fun `tints are pale enough to sit behind an icon`() {
        everyHue.forEach { hue ->
            assertTrue(luminance(hueTintOpaque(hue)) > 0.6, "hue $hue tint is too dark for a tile")
        }
    }
}
