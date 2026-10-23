package com.rider.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class TutorialActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var btnBack: Button
    private lateinit var txtTestType: TextView
    private lateinit var txtConnectivityLabel: TextView
    private lateinit var viewConnectivityDot: View
    private lateinit var txtNotConnectivity: TextView

    private var testType: String = "proxymity"
    private val mainHandler = Handler(Looper.getMainLooper())

    // Proximity Sensor
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null

    // Bluetooth
    private val bluetoothHelper by lazy { BluetoothHelper(this) }

    // Voice Test Audio Monitoring
    @Volatile
    private var isVoiceTesting = false
    private var voiceTestThread: Thread? = null
    private var voiceAudioRecord: AudioRecord? = null
    private var resetVoiceTimeoutRunnable: Runnable? = null

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

        // Initial state: not connected, white dot
        setConnectivityState(false)

        viewConnectivityDot.setOnClickListener {
            checkCurrentTest()
        }

        checkCurrentTest()
    }

    override fun onResume() {
        super.onResume()
        if (testType == "proxymity" && proximitySensor != null) {
            sensorManager?.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_UI)
        } else if (testType == "voice") {
            startVoiceLiveTest()
        }
        checkCurrentTest()
    }

    override fun onPause() {
        super.onPause()
        if (testType == "proxymity") {
            sensorManager?.unregisterListener(this)
        } else if (testType == "voice") {
            stopVoiceLiveTest()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (testType != "proxymity" || event == null) return
        if (event.sensor.type == Sensor.TYPE_PROXIMITY) {
            val distance = event.values[0]
            val maxRange = proximitySensor?.maximumRange ?: 5f
            val isHandNear = distance < maxRange || distance <= 2.5f

            // Real hardware check: when hand is detected, dot turns Sky Blue!
            // When away, turns white and shows not conectyvity.
            setConnectivityState(isHandNear)
            if (isHandNear) {
                triggerHapticFeedback()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun checkCurrentTest() {
        when (testType) {
            "blutooth" -> {
                // Real Bluetooth check: if not connected, display not conectyvity
                try {
                    val state = bluetoothHelper.getEarbudsState()
                    setConnectivityState(state.connected)
                } catch (_: Exception) {
                    setConnectivityState(false)
                }
            }
            "voice" -> {
                val hasAudioPerm = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (!hasAudioPerm) {
                    setConnectivityState(false)
                }
            }
            "proxymity" -> {
                if (proximitySensor == null) {
                    setConnectivityState(false)
                }
            }
        }
    }

    private fun startVoiceLiveTest() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            setConnectivityState(false)
            return
        }
        if (isVoiceTesting) return
        isVoiceTesting = true

        voiceTestThread = Thread {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val frameSamples = 320
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, 4096)

            try {
                voiceAudioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (voiceAudioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    voiceAudioRecord?.startRecording()
                    val frame = ShortArray(frameSamples)
                    var baseline = 150.0

                    while (isVoiceTesting) {
                        val read = voiceAudioRecord?.read(frame, 0, frame.size) ?: 0
                        if (read <= 0) {
                            Thread.sleep(15)
                            continue
                        }

                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = frame[i]
                            sumSq += (s * s).toDouble()
                        }
                        val rms = sqrt(sumSq / read)
                        baseline = (baseline * 0.95) + (rms * 0.05)
                        val threshold = maxOf(baseline * 2.2, 450.0)

                        if (rms > threshold) {
                            mainHandler.post {
                                setConnectivityState(true)
                                triggerHapticFeedback()

                                resetVoiceTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                                resetVoiceTimeoutRunnable = Runnable {
                                    setConnectivityState(false)
                                }
                                mainHandler.postDelayed(resetVoiceTimeoutRunnable!!, 1500)
                            }
                            Thread.sleep(300)
                        }

                        Thread.sleep(30)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try {
                    voiceAudioRecord?.stop()
                    voiceAudioRecord?.release()
                } catch (_: Exception) {}
                voiceAudioRecord = null
            }
        }.apply {
            name = "RiderVoiceTestThread"
            start()
        }
    }

    private fun stopVoiceLiveTest() {
        isVoiceTesting = false
        try {
            voiceTestThread?.interrupt()
        } catch (_: Exception) {}
        voiceTestThread = null

        try {
            voiceAudioRecord?.stop()
            voiceAudioRecord?.release()
        } catch (_: Exception) {}
        voiceAudioRecord = null
        resetVoiceTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
    }

    /**
     * White dot when disconnected, Sky Blue dot when connectivity found.
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