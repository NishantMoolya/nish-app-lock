package com.example.nish_app_lock

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint-first unlock screen.
 *
 * It is an opaque dark screen (app icon + name) that hides the locked app. Once it is
 * on screen it removes the blank LockOverlay cover, so the system fingerprint prompt
 * appears on top of THIS screen, not underneath a PIN overlay.
 *
 *  - fingerprint OK              -> mark app unlocked, close
 *  - cancel / Use PIN / lockout  -> show the PIN overlay, then close
 */
class BiometricActivity : FragmentActivity() {

    companion object {
        private const val TAG = "BiometricActivity"

        @Volatile
        var showing = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pkg: String? = null
    private var promptStarted = false
    private var done = false
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pkg = intent.getStringExtra("package")
        val target = pkg
        if (target == null || !BiometricHelper.isAvailable(this)) {
            if (target != null) LockOverlay.show(AppLockService.instance ?: this, target, autoBiometric = false)
            finish()
            return
        }
        showing = true
        setContentView(buildCover(target))

        // Back press on the cover must not expose the locked app: go to the PIN pad.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                fallbackToPin()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (promptStarted || pkg == null) return
        promptStarted = true

        // Let this opaque screen draw first, then drop the blank overlay cover, then
        // show the system prompt so it sits on top.
        handler.postDelayed({
            if (isFinishing || leaving) return@postDelayed
            LockOverlay.hide()
            handler.postDelayed({ if (!isFinishing && !leaving) startPrompt() }, 60)
        }, 120)
    }

    private fun startPrompt() {
        val target = pkg ?: return
        val label = appLabel(target)
        BiometricHelper.authenticate(
            activity = this,
            title = "Unlock $label",
            subtitle = "Touch the fingerprint sensor",
            negativeText = "Use PIN",
            onSuccess = {
                if (done) return@authenticate
                done = true
                Log.i(TAG, "Fingerprint OK for $target")
                AppLockService.unlockedPkg = target
                LockOverlay.hide()
                finish()
            },
            onFailure = { code, msg ->
                Log.i(TAG, "Fingerprint not used ($code): $msg; falling back to PIN")
                fallbackToPin()
            }
        )
    }

    private fun fallbackToPin() {
        val target = pkg ?: return
        if (done || leaving) return
        done = true
        // Show PIN overlay first, then close this screen so the locked app never flashes.
        LockOverlay.show(AppLockService.instance ?: applicationContext, target, autoBiometric = false)
        handler.postDelayed({ finish() }, 150)
    }

    // User left (Home / Recents / screen off): cancel quietly; no overlay re-shown here.
    override fun onStop() {
        super.onStop()
        if (!isFinishing) {
            leaving = true
            finish()
        }
    }

    override fun onDestroy() {
        showing = false
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun appLabel(target: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
    } catch (e: Exception) {
        "app"
    }

    private fun dp(v: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun buildCover(target: String): LinearLayout {
        val icon = try { packageManager.getApplicationIcon(target) } catch (e: Exception) { null }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(68), dp(68)).apply { bottomMargin = dp(16) }
                if (icon != null) setImageDrawable(icon) else setImageResource(android.R.drawable.ic_lock_lock)
            })
            addView(TextView(context).apply {
                text = appLabel(target)
                textSize = 22f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "App is locked"
                textSize = 14f
                setTextColor(Color.parseColor("#94A3B8"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            })
        }
    }
}
