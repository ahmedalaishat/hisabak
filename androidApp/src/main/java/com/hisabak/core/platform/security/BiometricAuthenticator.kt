package com.hisabak.core.platform.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.hisabak.core.domain.security.AuthAvailability

/**
 * Android glue around [BiometricPrompt]. The allowed authenticators are biometric **or** the device
 * credential (PIN/pattern/password), so the OS provides the fallback and we never build our own PIN
 * screen. Kept free of business logic; the lock gate and Settings both reuse it. For the planned CMP
 * migration this is the Android implementation behind the platform-agnostic [AuthAvailability] /
 * `shouldLock` domain layer.
 */
class BiometricAuthenticator(private val context: Context) {

    fun availability(): AuthAvailability =
        when (BiometricManager.from(context).canAuthenticate(ALLOWED)) {
            BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> AuthAvailability.NoneEnrolled
            else -> AuthAvailability.Unavailable
        }

    /** Shows the system prompt. [onResult] gets `true` on success, `false` on cancel/error/lockout. */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onResult: (Boolean) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onResult(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onResult(false)
                }
            },
        )
        // No negative button: DEVICE_CREDENTIAL is the fallback, and setting one throws when it's allowed.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED)
            .build()
        prompt.authenticate(info)
    }

    private companion object {
        const val ALLOWED = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    }
}
