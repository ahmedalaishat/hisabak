package com.hisabak.core.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    @Test
    fun `disabled never locks`() {
        assertFalse(shouldLock(enabled = false, lastBackgroundedAtMillis = null, nowMillis = 1_000))
        assertFalse(shouldLock(enabled = false, lastBackgroundedAtMillis = 0, nowMillis = 10_000_000))
    }

    @Test
    fun `cold start locks when enabled`() {
        assertTrue(shouldLock(enabled = true, lastBackgroundedAtMillis = null, nowMillis = 1_000))
    }

    @Test
    fun `within grace window stays unlocked`() {
        val now = 100_000L
        val backgrounded = now - (APP_LOCK_GRACE_MILLIS - 1)
        assertFalse(shouldLock(enabled = true, lastBackgroundedAtMillis = backgrounded, nowMillis = now))
    }

    @Test
    fun `past grace window locks`() {
        val now = 100_000L
        val backgrounded = now - (APP_LOCK_GRACE_MILLIS + 1)
        assertTrue(shouldLock(enabled = true, lastBackgroundedAtMillis = backgrounded, nowMillis = now))
    }

    @Test
    fun `exactly at grace boundary stays unlocked`() {
        val now = 100_000L
        val backgrounded = now - APP_LOCK_GRACE_MILLIS
        assertFalse(shouldLock(enabled = true, lastBackgroundedAtMillis = backgrounded, nowMillis = now))
    }
}
