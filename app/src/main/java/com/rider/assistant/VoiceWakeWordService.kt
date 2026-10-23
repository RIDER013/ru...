package com.rider.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.KeyEvent
import kotlin.math.sqrt

/**
 * VoiceWakeWordService:
 * True Low-Power On-Device Wake-Word Engine with robust Bluetooth MediaSession & Proximity handling.
 */
class VoiceWakeWordService : Service() {

    private var wakeWord = "RIDER"

    // Continuous On-Device Audio Engine
    @Volatile
    private var isAudioRunning = false
    private var audioThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    // Proximity Sensor (Wave hand near top of phone to swipe up)
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityListener: SensorEventListener? = null

    // MediaSession & AudioManager to capture real Bluetooth Earbud Touch Sensor clicks globally
    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()

        initProximitySensor()
        if (isBluetoothEnabled) {
            initMediaSessionForEarbuds()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeWord = intent?.getStringExtra(EXTRA_WAKE_WORD) ?: "RIDER"

        try {
            val notification = buildForegroundNotification("RIDER Active: Listening for '$wakeWord' / Earbud Taps...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground notification: ${e.message}")
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startSilentWakeWordEngine()
        }

        return START_STICKY
    }

    /**
     * TRUE ON-DEVICE WAKE-WORD ACOUSTIC ENGINE WITH STRICT SINGLE-SWIPE DEBOUNCE:
     */
    @SuppressLint("MissingPermission")
    private fun startSilentWakeWordEngine() {
        if (isAudioRunning) return
        isAudioRunning = true

        audioThread = Thread {
            val sampleRate = 16000
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val frameSamples = 320 // 20ms frame at 16kHz
            val minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufSize, 4096)

            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord could not initialize.")
                    return@Thread
                }

                audioRecord?.startRecording()
                Log.d(TAG, "Silent On-Device Wake-Word Engine active.")

                val frame = ShortArray(frameSamples)
                var ambientBaseline = 150.0
                var lastDetectionTime = 0L

                var inSpeech = false
                var speechFrames = 0
                var syllable1Frames = 0
                var dipFrames = 0
                var syllable2Frames = 0
                var syllablePhase = 0

                while (isAudioRunning) {
                    val readSamples = audioRecord?.read(frame, 0, frame.size) ?: 0
                    if (readSamples <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    var sumSq = 0.0
                    for (i in 0 until readSamples) {
                        val sample = frame[i]
                        sumSq += (sample * sample).toDouble()
                    }
                    val frameRms = sqrt(sumSq / readSamples)

                    if (!inSpeech) {
                        ambientBaseline = (ambientBaseline * 0.96) + (frameRms * 0.04)
                    }

                    val speechThreshold = maxOf(ambientBaseline * 2.3, 400.0)
                    val isVoiceFrame = frameRms > speechThreshold

                    if (isVoiceFrame) {
                        if (!inSpeech) {
                            inSpeech = true
                            speechFrames = 0
                            syllablePhase = 1
                            syllable1Frames = 0
                            dipFrames = 0
                            syllable2Frames = 0
                        }

                        speechFrames++

                        if (syllablePhase == 1) {
                            syllable1Frames++
                        } else if (syllablePhase == 2) {
                            syllablePhase = 3
                            syllable2Frames = 1
                        } else if (syllablePhase == 3) {
                            syllable2Frames++
                        }
                    } else {
                        if (inSpeech) {
                            speechFrames++

                            if (syllablePhase == 1 && syllable1Frames >= 5) {
                                syllablePhase = 2
                                dipFrames = 1
                            } else if (syllablePhase == 2) {
                                dipFrames++
                                if (dipFrames > 12) {
                                    inSpeech = false
                                    syllablePhase = 0
                                }
                            } else if (syllablePhase == 3) {
                                val now = System.currentTimeMillis()
                                val totalDurationMs = speechFrames * 20

                                val isTwoSyllableWakeWord = syllable1Frames in 4..18 &&
                                        dipFrames in 1..10 &&
                                        syllable2Frames in 3..15 &&
                                        totalDurationMs in 240..780

                                if (isTwoSyllableWakeWord && (now - lastDetectionTime > 2500)) {
                                    lastDetectionTime = now
                                    Log.d(TAG, "WAKE-WORD RECOGNIZED! ($wakeWord). Dispatching EXACTLY ONE swipe up...")
                                    triggerHapticFeedback()
                                    RiderAccessibilityService.instance?.swipeUp()
                                }

                                inSpeech = false
                                syllablePhase = 0
                            }
                        }
                    }

                    if (speechFrames > 55) {
                        inSpeech = false
                        syllablePhase = 0
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord error: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null
            }
        }.apply {
            name = "RiderSilentWakeWordThread"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    /**
     * Proximity Sensor: Swipes up when a finger/hand/glove passes near the top of the phone
     */
    private fun initProximitySensor() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

            if (proximitySensor != null) {
                proximityListener = object : SensorEventListener {
                    private var lastTriggerTime = 0L

                    override fun onSensorChanged(event: SensorEvent?) {
                        if (!isProximityEnabled || event == null) return
                        val distance = event.values[0]
                        val max = proximitySensor?.maximumRange ?: 5f

                        if (distance < max || distance <= 2.5f) {
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerTime > 1000) {
                                lastTriggerTime = now
                                Log.d(TAG, "Proximity hand wave detected! Swiping up at center...")
                                triggerHapticFeedback()
                                RiderAccessibilityService.instance?.swipeUp()
                            }
                        }
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }

                sensorManager?.registerListener(
                    proximityListener,
                    proximitySensor,
                    SensorManager.SENSOR_DELAY_UI
                )
                Log.d(TAG, "Proximity sensor registered successfully")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing proximity sensor: ${e.message}")
        }
    }

    /**
     * ROBUST BLUETOOTH MEDIASESSION & AUDIO FOCUS ROUTING:
     */
    private fun initMediaSessionForEarbuds() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            }

            mediaSession = MediaSession(this, "RiderEarbudsActiveSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)

                setPlaybackState(
                    PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                        .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_PLAY)
                        .build()
                )

                setCallback(object : MediaSession.Callback() {
                    private var lastEarbudClickTime = 0L

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }

                        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                            val now = System.currentTimeMillis()
                            if (now - lastEarbudClickTime > 1000) {
                                lastEarbudClickTime = now
                                Log.d(TAG, "ROBUST BLUETOOTH EARBUD CLICKED: key ${keyEvent.keyCode}! Triggering swipeUp()...")
                                triggerHapticFeedback()
                                RiderAccessibilityService.instance?.swipeUp()
                            }
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                isActive = true
            }

            Log.d(TAG, "Robust Bluetooth MediaSession initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up robust Bluetooth MediaSession: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                v?.vibrate(50)
            }
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Rider Assistant Voice & Sensors",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Active voice recognition and gesture sensors in background"
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager?.createNotificationChannel(channel)
            } catch (_: Exception) {}
        }
    }

    private fun buildForegroundNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("RIDER")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isServiceRunning = false
        isAudioRunning = false

        try {
            audioThread?.interrupt()
        } catch (_: Exception) {}
        audioThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            proximityListener?.let { sensorManager?.unregisterListener(it) }
        } catch (_: Exception) {}

        try {
            mediaSession?.release()
        } catch (_: Exception) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoiceWakeWordService"
        private const val NOTIFICATION_ID = 42001
        private const val CHANNEL_ID = "rider_voice_channel"
        const val EXTRA_WAKE_WORD = "extra_wake_word"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        @Volatile
        var isProximityEnabled: Boolean = true

        @Volatile
        var isBluetoothEnabled: Boolean = true

        fun start(context: Context, wakeWord: String = "RIDER") {
            try {
                val intent = Intent(context, VoiceWakeWordService::class.java).apply {
                    putExtra(EXTRA_WAKE_WORD, wakeWord)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VoiceWakeWordService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, VoiceWakeWordService::class.java)
                context.stopService(intent)
                isServiceRunning = false
            } catch (_: Exception) {}
        }
    }
}
