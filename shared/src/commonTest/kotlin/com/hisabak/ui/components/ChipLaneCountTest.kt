package com.hisabak.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class ChipLaneCountTest {

    @Test
    fun `short lists stay a single row`() {
        assertEquals(1, chipLaneCount(0))
        assertEquals(1, chipLaneCount(1))
        assertEquals(1, chipLaneCount(4))
    }

    @Test
    fun `more than four chips split into two lanes`() {
        assertEquals(2, chipLaneCount(5))
        assertEquals(2, chipLaneCount(8))
    }

    @Test
    fun `more than eight chips fill three lanes`() {
        assertEquals(3, chipLaneCount(9))
        assertEquals(3, chipLaneCount(50))
    }
}
