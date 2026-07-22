package com.hisabak.core.domain.security

/** Whether the device can satisfy an app-lock challenge right now. */
enum class AuthAvailability {
    /** A biometric or device credential is enrolled and ready. */
    Available,

    /** The hardware/OS supports a lock, but nothing is enrolled yet — route the user to set one up. */
    NoneEnrolled,

    /** No usable authenticator on this device. */
    Unavailable,
}

/** Default grace window: a quick app-switch (e.g. to copy an SMS code) shouldn't force re-auth. */
const val APP_LOCK_GRACE_MILLIS: Long = 30_000L

/**
 * Decide whether the app should present the lock when returning to the foreground. Cold start
 * (`lastBackgroundedAtMillis == null`) always locks when enabled. Pure so it is unit-testable
 * without the Android lifecycle — and free of platform types, so it moves to commonMain as-is for
 * the planned Compose Multiplatform migration.
 */
fun shouldLock(
    enabled: Boolean,
    lastBackgroundedAtMillis: Long?,
    nowMillis: Long,
    graceMillis: Long = APP_LOCK_GRACE_MILLIS,
): Boolean {
    if (!enabled) return false
    if (lastBackgroundedAtMillis == null) return true
    return nowMillis - lastBackgroundedAtMillis > graceMillis
}
