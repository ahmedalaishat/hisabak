package com.hisabak.feature.category.domain

import kotlin.math.abs

/**
 * Category colors are stored as short keys. Two shapes exist:
 *
 *  - a named palette key ("green", "blue", …) — the hand-tuned originals;
 *  - a custom hue, `h<0-359>` ("h210") — the user picked a hue and the app derives the actual
 *    shades per theme, so contrast and dark mode stay ours to guarantee.
 *
 * Only the hue is stored, never a rendered color: a hex would have one value, and every surface
 * here needs a different one in light vs dark.
 */
object CategoryColor {

    /** Representative hue of each named palette key, so custom and named colors can be compared. */
    val namedHues: Map<String, Int> = mapOf(
        "red" to 25,
        "orange" to 55,
        "green" to 155,
        "teal" to 185,
        "blue" to 250,
        "purple" to 300,
        "pink" to 350,
    )

    /** The key for a custom hue, normalized into 0..359. */
    fun customKey(hue: Int): String = "h${((hue % 360) + 360) % 360}"

    /** The hue a custom key carries, or null when [key] isn't one. */
    fun hueOf(key: String?): Int? {
        if (key == null || key.length !in 2..4 || key[0] != 'h') return null
        val digits = key.substring(1)
        if (!digits.all { it in '0'..'9' }) return null
        return digits.toInt().takeIf { it in 0..359 }
    }

    /** The hue a key represents — custom or named. Gray (and anything unknown) has none. */
    fun hueFor(key: String?): Int? = hueOf(key) ?: namedHues[key]

    /** Shortest distance between two hues on the wheel, 0..180. */
    fun hueDistance(a: Int, b: Int): Int {
        val d = abs(a - b) % 360
        return if (d > 180) 360 - d else d
    }

    /**
     * The hue sitting in the widest gap between those already [used] — the default for a new
     * category, so two categories don't land on the same donut slice color by accident.
     */
    fun mostDistinctHue(used: List<Int>): Int {
        val hues = used.map { ((it % 360) + 360) % 360 }.distinct().sorted()
        if (hues.isEmpty()) return namedHues.getValue("blue")
        if (hues.size == 1) return (hues[0] + 180) % 360

        var bestStart = hues.last()
        var bestGap = 360 - hues.last() + hues.first()
        for (i in 0 until hues.size - 1) {
            val gap = hues[i + 1] - hues[i]
            if (gap > bestGap) {
                bestGap = gap
                bestStart = hues[i]
            }
        }
        return (bestStart + bestGap / 2) % 360
    }

    /**
     * Hues closer than this read as "the same color" in a donut slice or a list of tiles — the
     * threshold for warning that another category already owns the color.
     */
    const val COLLISION_DEGREES = 20

    /** Whether [hue] is close enough to [other] that the two would be hard to tell apart. */
    fun collides(hue: Int, other: Int): Boolean = hueDistance(hue, other) < COLLISION_DEGREES
}
