package com.hisabak.core.domain.security

/** Platform capability check for the biometric/device-credential app lock. Android's
 *  `BiometricAuthenticator` implements it; iOS stubs it as unavailable until Phase B. */
interface BiometricAvailability {
    fun availability(): AuthAvailability
}
