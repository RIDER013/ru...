package com.rider.assistant

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class RiderAccessibilityService : AccessibilityService() {

    private var accessibilityButtonCallback: AccessibilityButtonController.AccessibilityButtonCallback? = null
    private var lastSwipeTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Privacy: Rider Assistant does not inspect or read screen contents
    }

    override fun onInterrupt() {
        Log.d(TAG, "RiderAccessibilityService interrupted")
    }

    /**
     * Intercepts Bluetooth earbud touch sensor taps (Media keys like Play/Pause, Headset Hook)
     * system-wide at Accessibility Service level to trigger instant upward swipe.
     */
    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event != null && event.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            Log.d(TAG, "AccessibilityService intercepted key event: $keyCode. Triggering swipeUp()...")
            val success = swipeUp()
            if (success) {
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    /**
     * Executes the upward swipe gesture on the center of the screen
     * Duration: 350ms
     * Start: Center X, 82% Y (bottom area)
     * End: Center X, 25% Y (top area)
     * Operates system-wide, even when Rider Assistant is minimized or closed.
     */
    fun swipeUp(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastSwipeTime < 400) {
            // Debounce rapid duplicate taps
            return false
        }
        lastSwipeTime = now

        triggerHapticFeedback()

        return try {
            val metrics = resources.displayMetrics
            val x = metrics.widthPixels / 2f
            val startY = metrics.heightPixels * 0.82f
            val endY = metrics.heightPixels * 0.25f

            val path = Path().apply {
                moveTo(x, startY)
                lineTo(x, endY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(path, 0, 350)
                )
                .build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.d(TAG, "Swipe gesture completed successfully")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Swipe gesture cancelled")
                }
            }, null)

            Log.d(TAG, "Swipe gesture dispatched: $dispatched")
            dispatched
        } catch (e: Exception) {
            Log.e(TAG, "Error executing swipe: ${e.message}")
            false
        }
    }

    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(45)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "RiderAccessibilityService connected successfully")

        // Register Floating Accessibility Button tap handler for Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val controller = accessibilityButtonController
                accessibilityButtonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        Log.d(TAG, "Floating Accessibility Button tapped! Triggering swipeUp()...")
                        swipeUp()
                    }

                    override fun onAvailabilityChanged(
                        controller: AccessibilityButtonController,
                        available: Boolean
                    ) {
                        Log.d(TAG, "Accessibility button availability changed: $available")
                    }
                }
                controller.registerAccessibilityButtonCallback(accessibilityButtonCallback!!)
                Log.d(TAG, "Registered AccessibilityButtonCallback successfully for floating icon tap")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register accessibility button callback: ${e.message}")
            }
        }
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && accessibilityButtonCallback != null) {
            try {
                accessibilityButtonController.unregisterAccessibilityButtonCallback(accessibilityButtonCallback!!)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering accessibility button callback: ${e.message}")
            }
            accessibilityButtonCallback = null
        }
        instance = null
        super.onDestroy()
        Log.d(TAG, "RiderAccessibilityService destroyed")
    }

    companion object {
        private const val TAG = "RiderAccessibility"

        @Volatile
        var instance: RiderAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }
}
