package com.hisabak.core.platform

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {

    @Test
    fun `platform name is not blank`() {
        assertTrue(getPlatform().name.isNotBlank())
    }
}
