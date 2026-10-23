package com.rider.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SensorEventListener {

    // Toggle states for Start Window
    private var voiceOn = true
    private var proxymityOn = true
    private var blutoothOn = true

    // Hardware Proximity Sensor
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var lastProximityTriggerTime = 0L

    // Bluetooth Helper
    private val bluetoothHelper by lazy { BluetoothHelper(this) }
    private var isBluetoothConnected = false

    // UI elements
    private lateinit var mainLayout: LinearLayout
    private lateinit var txtBluetoothIcon: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught exception on thread ${thread.name}:", throwable)
        }

        try {
            buildUi()
            requestRequiredPermissions()

            // Initialize hardware proximity sensor
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

            refreshBluetoothStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            refreshBluetoothStatus()
            if (proximitySensor != null && proxymityOn) {
                sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
            }
        } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!proxymityOn || event == null) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            if (distance < maxRange || distance <= 2.5f) {
                val now = System.currentTimeMillis()
                if (now - lastProximityTriggerTime > 1200) {
                    lastProximityTriggerTime = now
                    triggerManualSwipe()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun requestRequiredPermissions() {
        try {
            val needed = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    needed.add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (needed.isNotEmpty()) {
                requestPermissions(needed.toTypedArray(), PERMISSION_REQUEST_CODE)
            }
        } catch (_: Exception) {}
    }

    private fun refreshBluetoothStatus() {
        try {
            val state = bluetoothHelper.getEarbudsState()
            isBluetoothConnected = state.connected
            updateBluetoothIconColor()
        } catch (_: Exception) {
            isBluetoothConnected = false
            updateBluetoothIconColor()
        }
    }

    private fun updateBluetoothIconColor() {
        if (::txtBluetoothIcon.isInitialized) {
            if (isBluetoothConnected) {
                // If bluetooth connected: colourful (sky blue)
                txtBluetoothIcon.setTextColor(Color.parseColor("#38BDF8"))
            } else {
                // If not connected: white
                txtBluetoothIcon.setTextColor(Color.WHITE)
            }
        }
    }

    /**
     * Exact UI specified by the user:
     * - Top: ƦƗƉƐƦ Title + Top Right [Ʀ] in Pink + Bluetooth Icon below it
     * - Center: START button
     * - Bottom: TEST button + "created by ƦƗƉƐƦ"
     */
    private fun buildUi() {
        mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#080C14"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(40, 50, 40, 30)
        }

        // 1. TOP HEADER: ƦƗƉƐƦ and Top Right Ʀ (pink) + Bluetooth Icon below it
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleText = TextView(this).apply {
            text = "ƦƗƉƐƦ"
            textSize = 28f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleText)

        // Top right layout: Ʀ (pink) and bluetooth icon below it
        val topRightLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val pinkRText = TextView(this).apply {
            text = "Ʀ"
            textSize = 28f
            setTextColor(Color.parseColor("#FF2A85")) // Pink color
            setTypeface(null, Typeface.BOLD)
        }
        topRightLayout.addView(pinkRText)

        txtBluetoothIcon = TextView(this).apply {
            text = "ᛒ" // Bluetooth unicode symbol
            textSize = 22f
            setTextColor(if (isBluetoothConnected) Color.parseColor("#38BDF8") else Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 4, 0, 0)
        }
        topRightLayout.addView(txtBluetoothIcon)

        headerLayout.addView(topRightLayout)
        mainLayout.addView(headerLayout)

        // 2. CENTER: START BUTTON
        val centerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val startBtn = Button(this).apply {
            text = "START"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#0284C7", "#38BDF8", 120f)
            layoutParams = LinearLayout.LayoutParams(260, 260)
            setOnClickListener { showStartWindow() }
        }
        centerContainer.addView(startBtn)
        mainLayout.addView(centerContainer)

        // 3. BOTTOM SECTION: TEST BUTTON + "created by ƦƗƉƐƦ"
        val bottomContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val testBtn = Button(this).apply {
            text = "test"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = createRoundedDrawable("#1E293B", "#334155", 20f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
            setPadding(0, 26, 0, 26)
            setOnClickListener { showTestChoiceDialog() }
        }
        bottomContainer.addView(testBtn)

        val createdByText = TextView(this).apply {
            text = "created by ƦƗƉƐƦ"
            textSize = 10f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        bottomContainer.addView(createdByText)

        mainLayout.addView(bottomContainer)
        setContentView(mainLayout)
    }

    /**
     * Start window popup:
     * 1 ] voice             on
     * 2 ] proxymity    on
     * 3 ] blutooth   on
     */
    private fun showStartWindow() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 24)
            setBackgroundColor(Color.parseColor("#101827"))
        }

        val title = TextView(this).apply {
            text = "Start Configuration"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        }
        dialogView.addView(title)

        // 1 ] voice on
        val voiceSwitch = Switch(this).apply {
            text = "1 ] voice"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = voiceOn
            setPadding(0, 10, 0, 16)
        }
        dialogView.addView(voiceSwitch)

        // 2 ] proxymity on
        val proxSwitch = Switch(this).apply {
            text = "2 ] proxymity"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = proxymityOn
            setPadding(0, 10, 0, 16)
        }
        dialogView.addView(proxSwitch)

        // 3 ] blutooth on
        val btSwitch = Switch(this).apply {
            text = "3 ] blutooth"
            textSize = 14f
            setTextColor(Color.WHITE)
            isChecked = blutoothOn
            setPadding(0, 10, 0, 24)
        }
        dialogView.addView(btSwitch)

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Done") { _, _ ->
                voiceOn = voiceSwitch.isChecked
                proxymityOn = proxSwitch.isChecked
                blutoothOn = btSwitch.isChecked

                VoiceWakeWordService.isVoiceEnabled = voiceOn
                VoiceWakeWordService.isProximityEnabled = proxymityOn
                VoiceWakeWordService.isBluetoothEnabled = blutoothOn

                if (voiceOn || blutoothOn || proxymityOn) {
                    VoiceWakeWordService.start(this, "RIDER")
                } else {
                    VoiceWakeWordService.stop(this)
                }

                Toast.makeText(this, "ƦƗƉƐƦ Active in background!", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Test button popup options:
     * 1 ] voice             test
     * 2 ] proxymity    test
     * 3 ] blutooth       test
     */
    private fun showTestChoiceDialog() {
        val items = arrayOf(
            "1 ] voice             test",
            "2 ] proxymity    test",
            "3 ] blutooth       test"
        )

        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                val intent = Intent(this, TutorialActivity::class.java).apply {
                    putExtra("TEST_TYPE", when (which) {
                        0 -> "voice"
                        1 -> "proxymity"
                        else -> "blutooth"
                    })
                }
                startActivity(intent)
            }
            .show()
    }

    private fun triggerManualSwipe() {
        RiderAccessibilityService.instance?.swipeUp()
    }

    private fun createRoundedDrawable(fillHex: String, strokeHex: String, cornerRadius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.parseColor(fillHex))
            setStroke(2, Color.parseColor(strokeHex))
            this.cornerRadius = cornerRadius
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
}