package com.example.nish_app_lock

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity

/**
 * Invisible activity whose only job is to host the system BiometricPrompt.
 * The PIN overlay (LockOverlay) stays on screen underneath as the fallback:
 *  - fingerprint OK  -> mark the app as unlocked and dismiss the overlay
 *  - cancel / Use PIN / lockout -> just close; the user types their PIN
 */
class BiometricActivity : FragmentActivity() {

    companion object {
        private const val TAG = "BiometricActivity"

        @Volatile
        var showing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pkg = intent.getStringExtra("package")
        if (pkg == null || !BiometricHelper.isAvailable(this)) {
            finish()
            return
        }
        showing = true

        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            "app"
        }

        BiometricHelper.authenticate(
            activity = this,
            title = "Unlock $label",
            subtitle = "Touch the fingerprint sensor",
            negativeText = "Use PIN",
            onSuccess = {
                Log.i(TAG, "Fingerprint OK for $pkg")
                AppLockService.unlockedPkg = pkg
                LockOverlay.hide()
                finish()
            },
            onFailure = { code, msg ->
                Log.i(TAG, "Fingerprint not used ($code): $msg; falling back to PIN")
                finish()
            }
        )
    }

    // Leaving (Home / Recents / screen off) cancels the prompt; overlay + PIN remain.
    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        showing = false
        super.onDestroy()
    }
}
