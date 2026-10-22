package com.rider.assistant

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class RiderAccessibilityService : AccessibilityService() {

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

            val dispatched = dispatchGesture(gesture, null, null)
            Log.d(TAG, "Swipe gesture dispatched: $dispatched")
            dispatched
        } catch (e: Exception) {
            Log.e(TAG, "Error executing swipe: ${e.message}")
            false
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "RiderAccessibilityService connected successfully")
    }

    override fun onDestroy() {
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
