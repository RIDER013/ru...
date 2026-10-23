package com.rider.assistant

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var btnBack: Button
    private lateinit var txtTestType: TextView
    private lateinit var txtConnectivityLabel: TextView
    private lateinit var viewConnectivityDot: View
    private lateinit var txtNotConnectivity: TextView

    private var testType: String = "proxymity"
    private var isSensorTappedActive = false

    // Proximity Sensor
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        testType = intent.getStringExtra("TEST_TYPE") ?: "proxymity"

        btnBack = findViewById(R.id.btnBack)
        txtTestType = findViewById(R.id.txtTestType)
        txtConnectivityLabel = findViewById(R.id.txtConnectivityLabel)
        viewConnectivityDot = findViewById(R.id.viewConnectivityDot)
        txtNotConnectivity = findViewById(R.id.txtNotConnectivity)

        txtTestType.text = testType

        btnBack.setOnClickListener {
            finish()
        }

        // Initialize Proximity Sensor
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

        // Initial state: starts White with "not conectyvity"
        // NOT turned sky blue automatically when bluetooth is connected!
        setConnectivityState(false)

        // Tapping directly on the sensor dot toggles the Sky Blue light
        viewConnectivityDot.setOnClickListener {
            toggleSensor()
        }

        // Also tapping on connectivity label toggles the sensor test
        txtConnectivityLabel.setOnClickListener {
            toggleSensor()
        }
    }

    private fun toggleSensor() {
        isSensorTappedActive = !isSensorTappedActive
        setConnectivityState(isSensorTappedActive)
        triggerHapticFeedback()
    }

    override fun onResume() {
        super.onResume()
        if (testType == "proxymity" && proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        if (testType == "proxymity") {
            sensorManager?.unregisterListener(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (testType != "proxymity" || event == null) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isHandNear = distance < maxRange || distance <= 2.5f

            // If hardware hand proximity detected, light up Sky Blue; otherwise follow tap state
            if (isHandNear) {
                setConnectivityState(true)
                triggerHapticFeedback()
            } else if (!isSensorTappedActive) {
                setConnectivityState(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * White dot when disconnected/idle, Sky Blue dot when sensor is activated.
     * If not connected: shows "not conectyvity".
     */
    private fun setConnectivityState(isConnected: Boolean) {
        if (isConnected) {
            viewConnectivityDot.setBackgroundResource(R.drawable.circle_skyblue_dot)
            txtNotConnectivity.visibility = View.GONE
        } else {
            viewConnectivityDot.setBackgroundResource(R.drawable.circle_white_dot)
            txtNotConnectivity.visibility = View.VISIBLE
            txtNotConnectivity.text = "not conectyvity"
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(40)
            }
        } catch (_: Exception) {}
    }
}