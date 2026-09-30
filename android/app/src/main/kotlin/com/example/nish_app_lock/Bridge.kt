package com.example.nish_app_lock

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import androidx.fragment.app.FragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

object Prefs {
    private const val PREF_NAME = "applock_prefs"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_LOCKED_SET = "locked"
    private const val KEY_LOCKED_CSV = "locked_apps_csv"
    private const val KEY_BIOMETRIC = "biometric_enabled"

    fun get(c: Context) = c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun getLockedApps(c: Context): Set<String> {
        val sp = get(c)
        val csv = sp.getString(KEY_LOCKED_CSV, null)
        if (csv != null) {
            if (csv.isEmpty()) return emptySet()
            return csv.split(",").filter { it.isNotEmpty() }.toSet()
        }
        return sp.getStringSet(KEY_LOCKED_SET, emptySet()) ?: emptySet()
    }

    @Synchronized
    fun setLockedApps(c: Context, apps: Collection<String>) {
        val csv = apps.joinToString(",")
        get(c).edit()
            .putString(KEY_LOCKED_CSV, csv)
            .putStringSet(KEY_LOCKED_SET, HashSet(apps))
            .commit()
    }

    /** Fingerprint is the primary unlock method, so it defaults to ON (when hardware + enrollment exist). */
    fun isBiometricEnabled(c: Context): Boolean = get(c).getBoolean(KEY_BIOMETRIC, true)

    fun setBiometricEnabled(c: Context, enabled: Boolean) {
        get(c).edit().putBoolean(KEY_BIOMETRIC, enabled).commit()
    }

    fun getPinHash(c: Context): String? = get(c).getString(KEY_PIN_HASH, null)

    fun setPinHash(c: Context, hash: String?) {
        get(c).edit().putString(KEY_PIN_HASH, hash).commit()
    }
}

object Bridge {
    fun register(activity: Activity, engine: FlutterEngine) {
        MethodChannel(engine.dartExecutor.binaryMessenger, "applock/native")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getApps" -> Thread {
                        val apps = loadApps(activity)
                        activity.runOnUiThread { result.success(apps) }
                    }.start()

                    "hasPin" -> result.success(Prefs.getPinHash(activity) != null)
                    "getPinHash" -> result.success(Prefs.getPinHash(activity))
                    "setPinHash" -> {
                        Prefs.setPinHash(activity, call.argument<String>("hash"))
                        result.success(null)
                    }

                    "verifyPin" -> {
                        val pin = call.argument<String>("pin") ?: ""
                        val expected = Prefs.getPinHash(activity)
                        val hash = HashUtil.sha256("applock::$pin")
                        result.success(expected != null && expected == hash)
                    }

                    // ---- biometrics ----
                    "isBiometricAvailable" -> result.success(BiometricHelper.isAvailable(activity))
                    "isBiometricEnabled" -> result.success(Prefs.isBiometricEnabled(activity))
                    "setBiometricEnabled" -> {
                        Prefs.setBiometricEnabled(activity, call.argument<Boolean>("enabled") ?: false)
                        result.success(null)
                    }
                    "authenticateBiometric" -> {
                        val fa = activity as? FragmentActivity
                        if (fa == null || !BiometricHelper.isAvailable(activity)) {
                            result.success(false)
                        } else {
                            var replied = false
                            fun reply(ok: Boolean) {
                                if (!replied) { replied = true; result.success(ok) }
                            }
                            BiometricHelper.authenticate(
                                fa,
                                title = call.argument<String>("title") ?: "Unlock",
                                subtitle = call.argument<String>("subtitle"),
                                onSuccess = { reply(true) },
                                onFailure = { _, _ -> reply(false) }
                            )
                        }
                    }

                    "getLockedApps" ->
                        result.success(Prefs.getLockedApps(activity).toList())

                    "setLockedApps" -> {
                        val list = call.argument<List<String>>("apps") ?: emptyList()
                        Prefs.setLockedApps(activity, list)
                        result.success(null)
                    }

                    "isAccessibilityEnabled" -> result.success(isAccessibilityOn(activity))

                    "openAccessibilitySettings" -> {
                        try {
                            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        } catch (e: Exception) {
                            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
                        }
                        result.success(null)
                    }

                    "canDrawOverlays" -> result.success(Settings.canDrawOverlays(activity))

                    "requestOverlay" -> {
                        try {
                            activity.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${activity.packageName}")
                                )
                            )
                        } catch (e: Exception) {
                            activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
                        }
                        result.success(null)
                    }

                    // ---- used by LockActivity ----
                    "getTargetName" -> {
                        val pkg = activity.intent.getStringExtra("package")
                        val name = try {
                            val pm = activity.packageManager
                            pkg?.let { pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString() }
                        } catch (e: Exception) { null }
                        result.success(name ?: "This app")
                    }

                    "unlocked" -> {
                        val pkg = activity.intent.getStringExtra("package")
                        AppLockService.unlockedPkg = pkg
                        result.success(null)
                        activity.finish()
                    }

                    "goHome" -> {
                        val service = AppLockService.instance
                        if (service != null) {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                        } else {
                            activity.startActivity(
                                Intent(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        result.success(null)
                        activity.finish()
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun loadApps(ctx: Context): List<Map<String, Any?>> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, android.content.pm.PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolveInfos
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { ri ->
                try {
                    val label = ri.loadLabel(pm).toString()
                    val pkg = ri.activityInfo.packageName
                    val icon = try {
                        iconBytes(ri.loadIcon(pm))
                    } catch (e: Throwable) {
                        null
                    }
                    mapOf<String, Any?>(
                        "name" to label,
                        "package" to pkg,
                        "icon" to icon
                    )
                } catch (e: Throwable) {
                    null
                }
            }
            .sortedBy { (it["name"] as String).lowercase() }
    }

    private fun iconBytes(drawable: android.graphics.drawable.Drawable): ByteArray? {
        return try {
            val size = 72
            val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable && drawable.bitmap != null) {
                Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
            } else {
                val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, size, size)
                drawable.draw(canvas)
                bmp
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.toByteArray()
        } catch (e: Throwable) {
            null
        }
    }

    private fun isAccessibilityOn(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val cn = ComponentName(ctx, AppLockService::class.java)
        val full = cn.flattenToString()
        val short = cn.flattenToShortString()
        return enabled.split(':').any {
            it.equals(full, true) || it.equals(short, true) ||
                    (it.contains(ctx.packageName) && it.contains("AppLockService"))
        }
    }
}
