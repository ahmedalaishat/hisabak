package com.hisabak.feature.category.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CategoryColorTest {

    @Test
    fun `a custom key round-trips its hue`() {
        (0 until 360 step 17).forEach {
            assertEquals(it, CategoryColor.hueOf(CategoryColor.customKey(it)))
        }
    }

    @Test
    fun `custom keys normalize onto the wheel`() {
        assertEquals("h10", CategoryColor.customKey(370))
        assertEquals("h350", CategoryColor.customKey(-10))
        assertEquals("h0", CategoryColor.customKey(360))
    }

    @Test
    fun `named and malformed keys carry no custom hue`() {
        listOf("green", "gray", null, "h", "h360", "h999", "hue", "h-1", "h1x").forEach {
            assertNull(CategoryColor.hueOf(it), "should not parse as a custom hue: $it")
        }
    }

    @Test
    fun `named palette keys still resolve to a hue for comparison`() {
        assertEquals(155, CategoryColor.hueFor("green"))
        assertEquals(210, CategoryColor.hueFor("h210"))
        assertNull(CategoryColor.hueFor("gray"), "gray has no hue to clash with")
        assertNull(CategoryColor.hueFor("nonsense"))
    }

    @Test
    fun `hue distance wraps the short way around`() {
        assertEquals(0, CategoryColor.hueDistance(10, 10))
        assertEquals(20, CategoryColor.hueDistance(350, 10))
        assertEquals(20, CategoryColor.hueDistance(10, 350))
        assertEquals(180, CategoryColor.hueDistance(0, 180))
        assertTrue(CategoryColor.hueDistance(0, 359) <= 180)
    }

    @Test
    fun `the first category gets a sensible color`() {
        assertEquals(CategoryColor.namedHues.getValue("blue"), CategoryColor.mostDistinctHue(emptyList()))
    }

    @Test
    fun `a second category lands opposite the first`() {
        assertEquals(200, CategoryColor.mostDistinctHue(listOf(20)))
    }

    @Test
    fun `the default sits in the widest gap`() {
        // 0, 90, 180 leaves 180..360 as the widest gap; its middle is 270.
        assertEquals(270, CategoryColor.mostDistinctHue(listOf(0, 90, 180)))
    }

    @Test
    fun `the default gap search wraps past 360`() {
        // 10, 40, 70 — the widest gap runs 70 -> 370, centred on 220.
        assertEquals(220, CategoryColor.mostDistinctHue(listOf(10, 40, 70)))
    }

    @Test
    fun `the suggested default never collides with what is already used`() {
        val used = listOf(20, 140, 260)
        val pick = CategoryColor.mostDistinctHue(used)
        used.forEach {
            assertFalse(CategoryColor.collides(pick, it), "default $pick collides with $it")
        }
    }

    @Test
    fun `nearby hues collide and distant ones do not`() {
        assertTrue(CategoryColor.collides(200, 210))
        assertTrue(CategoryColor.collides(5, 355), "collision detection must wrap")
        assertFalse(CategoryColor.collides(200, 260))
    }
}
