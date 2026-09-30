package com.example.nish_app_lock

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

class AppLockService : AccessibilityService() {

    companion object {
        private const val TAG = "AppLockService"

        @Volatile var instance: AppLockService? = null
        @Volatile var unlockedPkg: String? = null
        @Volatile var lockShowing = false
        @Volatile var lockActivityVisible = false
    }

    private var lastLaunch = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d(TAG, "Screen off: locking all apps and dismissing overlay")
                unlockedPkg = null
                LockOverlay.hide()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Accessibility service connected")

        if (!screenReceiverRegistered) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        }
    }

    override fun onDestroy() {
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screenReceiver", e)
            }
            screenReceiverRegistered = false
        }
        LockOverlay.hide()
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    // Windows from these packages should not count as "user left the app".
    private fun ignored(): Set<String> {
        val s = mutableSetOf(
            packageName,
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.forEach { s.add(it.packageName) }
        } catch (e: Exception) {
            // Ignore IMM exceptions
        }
        return s
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignored()) return

        // If user moved to a different app/launcher, previous unlock expires.
        if (pkg != unlockedPkg) {
            unlockedPkg = null
        }

        val locked = Prefs.getLockedApps(this)
        Log.d(TAG, "Window event: $pkg; isLocked=${pkg in locked}; unlockedPkg=$unlockedPkg")

        // If target app is NOT locked
        if (pkg !in locked) {
            if (LockOverlay.isShowing() && LockOverlay.currentPkg != pkg) {
                LockOverlay.hide()
            }
            return
        }

        // If target app is locked BUT user already entered PIN for this session
        if (pkg == unlockedPkg) {
            if (LockOverlay.isShowing()) {
                LockOverlay.hide()
            }
            return
        }

        // If overlay permission is granted, use instant WindowManager overlay
        if (Settings.canDrawOverlays(this)) {
            LockOverlay.show(this, pkg)
            return
        }

        // Fallback: If overlay permission is not granted yet, use LockActivity
        if (lockShowing) return
        val now = System.currentTimeMillis()
        if (now - lastLaunch < 700) return
        lastLaunch = now

        try {
            lockShowing = true
            Log.i(TAG, "Launching fallback LockActivity for $pkg")
            startActivity(
                Intent(this, LockActivity::class.java)
                    .putExtra("package", pkg)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
            )
            mainHandler.postDelayed({
                if (!lockActivityVisible && lockShowing) {
                    lockShowing = false
                }
            }, 2500)
        } catch (e: Exception) {
            Log.e(TAG, "Unable to launch lock activity for $pkg", e)
            lockShowing = false
        }
    }

    override fun onInterrupt() {}
}
