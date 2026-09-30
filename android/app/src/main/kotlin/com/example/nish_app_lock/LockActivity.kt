package com.example.nish_app_lock

import android.os.Bundle
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/** Runs the Dart function `lockMain` – the PIN overlay shown on top of a locked app. */
class LockActivity : FlutterActivity() {
    companion object {
        private const val TAG = "LockActivity"
    }

    override fun getDartEntrypointFunctionName() = "lockMain"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        Bridge.register(this, flutterEngine)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLockService.lockShowing = true
        Log.i(TAG, "Lock activity created for ${intent.getStringExtra("package")}")
    }

    override fun onResume() {
        super.onResume()
        AppLockService.lockActivityVisible = true
        Log.i(TAG, "Lock activity resumed")
    }

    override fun onPause() {
        AppLockService.lockActivityVisible = false
        super.onPause()
    }

    // If the user leaves the lock screen (Home / Recents), close it.
    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        AppLockService.lockActivityVisible = false
        AppLockService.lockShowing = false
        Log.i(TAG, "Lock activity destroyed")
        super.onDestroy()
    }
}
