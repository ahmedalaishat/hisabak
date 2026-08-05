package com.hisabak.core.platform.security

import com.hisabak.core.domain.security.AuthAvailability
import com.hisabak.core.domain.security.BiometricAvailability
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlinx.cinterop.ObjCObjectVar

/**
 * LocalAuthentication-backed app lock: biometrics with device-passcode fallback
 * (LAPolicyDeviceOwnerAuthentication — the iOS equivalent of Android's
 * BIOMETRIC_STRONG or DEVICE_CREDENTIAL prompt).
 */
class IosBiometricAuthenticator : BiometricAvailability {

    @OptIn(ExperimentalForeignApi::class)
    override fun availability(): AuthAvailability = memScoped {
        val error = alloc<ObjCObjectVar<NSError?>>()
        val can = LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error.ptr)
        when {
            can -> AuthAvailability.Available
            error.value?.code == LAErrorPasscodeNotSet -> AuthAvailability.NoneEnrolled
            else -> AuthAvailability.Unavailable
        }
    }

    /** Presents the system auth sheet; [onResult] is delivered on the main queue. */
    fun authenticate(reason: String, onResult: (Boolean) -> Unit) {
        LAContext().evaluatePolicy(LAPolicyDeviceOwnerAuthentication, reason) { ok, _ ->
            dispatch_async(dispatch_get_main_queue()) { onResult(ok) }
        }
    }
}
