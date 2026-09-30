package com.example.nish_app_lock

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object LockOverlay {
    private const val TAG = "LockOverlay"

    @Volatile
    private var overlayView: View? = null

    @Volatile
    var currentPkg: String? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())
    private var enteredPin = StringBuilder()
    private var isBusy = false

    fun isShowing(): Boolean = overlayView != null

    fun show(context: Context, targetPkg: String) {
        mainHandler.post {
            try {
                if (!Settings.canDrawOverlays(context)) {
                    Log.w(TAG, "Cannot draw overlay: permission not granted")
                    return@post
                }

                if (overlayView != null) {
                    if (currentPkg == targetPkg) return@post
                    hideImmediate(context)
                }

                currentPkg = targetPkg
                enteredPin.setLength(0)
                isBusy = false

                val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val view = createOverlayView(context, targetPkg)
                overlayView = view

                val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    type,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                wm.addView(view, params)
                Log.i(TAG, "Overlay shown for $targetPkg")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show overlay for $targetPkg", e)
                overlayView = null
                currentPkg = null
            }
        }
    }

    fun hide(context: Context? = null) {
        mainHandler.post {
            hideImmediate(context ?: AppLockService.instance)
        }
    }

    private fun hideImmediate(context: Context?) {
        val view = overlayView ?: return
        overlayView = null
        currentPkg = null
        enteredPin.setLength(0)
        isBusy = false

        try {
            val ctx = context ?: view.context
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(view)
            Log.i(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing overlay", e)
        }
    }

    private fun dp(ctx: Context, dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()

    private fun vibrate(context: Context, isError: Boolean = false) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isError) {
                    val timings = longArrayOf(0, 70, 70, 70)
                    val amplitudes = intArrayOf(0, 200, 0, 200)
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            } else {
                @Suppress("DEPRECATION")
                if (isError) {
                    vibrator.vibrate(longArrayOf(0, 70, 70, 70), -1)
                } else {
                    vibrator.vibrate(25)
                }
            }
        } catch (e: Throwable) {
            // Ignore vibration errors
        }
    }

    private fun goHome(context: Context) {
        val service = AppLockService.instance
        if (service != null) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        } else {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        hideImmediate(context)
    }

    private fun createOverlayView(context: Context, targetPkg: String): View {
        val root = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    goHome(context)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }

        // Full screen dark gradient / solid background (#0F172A = Slate 900)
        root.setBackgroundColor(Color.parseColor("#0F172A"))
        root.isFocusable = true
        root.isFocusableInTouchMode = true
        root.isClickable = true

        val pm = context.packageManager
        val appLabel = try {
            val info = pm.getApplicationInfo(targetPkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            "Locked App"
        }
        val appIcon = try {
            pm.getApplicationIcon(targetPkg)
        } catch (e: Exception) {
            null
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(dp(context, 24), 0, dp(context, 24), 0)
            }
        }

        // Top shield / icon container
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(context, 68), dp(context, 68)).apply {
                bottomMargin = dp(context, 16)
            }
            if (appIcon != null) {
                setImageDrawable(appIcon)
            } else {
                setImageResource(android.R.drawable.ic_lock_lock)
            }
        }
        container.addView(iconView)

        // App Name Label
        val titleView = TextView(context).apply {
            text = appLabel
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(titleView)

        // Subtitle
        val subtitleView = TextView(context).apply {
            text = "App is locked • Enter PIN"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8")) // Slate 400
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(context, 4)
                bottomMargin = dp(context, 24)
            }
        }
        container.addView(subtitleView)

        // PIN Indicators (4 dots)
        val dotsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(context, 12)
            }
        }

        val dotViews = Array(4) {
            View(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)).apply {
                    setMargins(dp(context, 10), 0, dp(context, 10), 0)
                }
            }
        }

        fun updateDots(length: Int, isError: Boolean = false) {
            val emptyDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(context, 2), Color.parseColor("#475569"))
                setColor(Color.TRANSPARENT)
            }
            val filledDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#6366F1")) // Indigo 500
            }
            val errorDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#EF4444")) // Red 500
            }

            for (i in 0 until 4) {
                dotViews[i].background = when {
                    isError -> errorDrawable
                    i < length -> filledDrawable
                    else -> emptyDrawable
                }
            }
        }
        updateDots(0)

        for (dot in dotViews) {
            dotsContainer.addView(dot)
        }
        container.addView(dotsContainer)

        // Error message text view
        val errorText = TextView(context).apply {
            text = "Incorrect PIN"
            textSize = 13f
            setTextColor(Color.parseColor("#EF4444"))
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(context, 28)
            )
        }
        container.addView(errorText)

        // Verification logic
        fun onPinEntered(pin: String) {
            if (isBusy) return
            isBusy = true

            val expectedHash = Prefs.getPinHash(context)
            val enteredHash = HashUtil.sha256("applock::$pin")

            if (expectedHash != null && expectedHash == enteredHash) {
                // Correct PIN!
                AppLockService.unlockedPkg = targetPkg
                hideImmediate(context)
            } else {
                // Wrong PIN!
                vibrate(context, isError = true)
                errorText.text = "Incorrect PIN. Try again."
                errorText.visibility = View.VISIBLE
                updateDots(4, isError = true)

                // Shake animation on dots
                val shake = ObjectAnimator.ofFloat(
                    dotsContainer,
                    "translationX",
                    0f, 25f, -25f, 20f, -20f, 10f, -10f, 0f
                ).apply {
                    duration = 450
                }
                shake.start()

                mainHandler.postDelayed({
                    enteredPin.setLength(0)
                    updateDots(0)
                    isBusy = false
                }, 600)
            }
        }

        fun onKeyTapped(digit: String) {
            if (isBusy || enteredPin.length >= 4) return
            errorText.visibility = View.INVISIBLE
            vibrate(context, isError = false)
            enteredPin.append(digit)
            updateDots(enteredPin.length)

            if (enteredPin.length == 4) {
                onPinEntered(enteredPin.toString())
            }
        }

        fun onBackspace() {
            if (isBusy || enteredPin.isEmpty()) return
            vibrate(context, isError = false)
            enteredPin.deleteCharAt(enteredPin.length - 1)
            errorText.visibility = View.INVISIBLE
            updateDots(enteredPin.length)
        }

        // Keypad creation
        fun createKey(text: String, isAction: Boolean = false, onClick: () -> Unit): View {
            val keySize = dp(context, 72)
            val btn = TextView(context).apply {
                this.text = text
                textSize = if (isAction) 15f else 26f
                setTypeface(null, if (isAction) Typeface.NORMAL else Typeface.BOLD)
                setTextColor(if (isAction) Color.parseColor("#94A3B8") else Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(keySize, keySize).apply {
                    setMargins(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6))
                }

                val normalColor = if (isAction) Color.TRANSPARENT else Color.parseColor("#1E293B")
                val normalBg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(normalColor)
                }

                background = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#38BDF8")),
                    normalBg,
                    normalBg
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick() }
            }
            return btn
        }

        val rows = arrayOf(
            arrayOf("1", "2", "3"),
            arrayOf("4", "5", "6"),
            arrayOf("7", "8", "9")
        )

        for (row in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (digit in row) {
                rowLayout.addView(createKey(digit) { onKeyTapped(digit) })
            }
            container.addView(rowLayout)
        }

        // Bottom row: Exit / Home, 0, Backspace
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        // Exit / Cancel button
        bottomRow.addView(createKey("Exit", isAction = true) {
            goHome(context)
        })

        // 0 key
        bottomRow.addView(createKey("0") { onKeyTapped("0") })

        // Backspace key
        bottomRow.addView(createKey("⌫", isAction = true) { onBackspace() })

        container.addView(bottomRow)
        root.addView(container)

        return root
    }
}
