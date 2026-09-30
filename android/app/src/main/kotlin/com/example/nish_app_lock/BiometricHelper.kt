package com.example.nish_app_lock

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Thin wrapper around AndroidX BiometricPrompt (fingerprint only, PIN is our own fallback). */
object BiometricHelper {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** True if the device has a fingerprint sensor AND at least one fingerprint is enrolled. */
    fun isAvailable(ctx: Context): Boolean = try {
        BiometricManager.from(ctx).canAuthenticate(AUTHENTICATORS) ==
                BiometricManager.BIOMETRIC_SUCCESS
    } catch (e: Throwable) {
        false
    }

    /**
     * Shows the system fingerprint prompt.
     * onFailure fires for cancel, the negative ("Use PIN") button, lockout and hard errors.
     * A single wrong fingerprint is NOT a failure; the system prompt stays open for retry.
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String? = null,
        negativeText: String = "Use PIN",
        onSuccess: () -> Unit,
        onFailure: (code: Int, message: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errorCode, errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info)
    }
}
