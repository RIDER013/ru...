package com.rider.assistant

import android.Manifest
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
import android.media.session.MediaSession
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import java.util.Locale

class VoiceWakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var wakeWord = "RIDER"

    // Proximity Sensor (Wave hand near top of phone to swipe up)
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityListener: SensorEventListener? = null

    // MediaSession to capture real Bluetooth Earbud Touch Sensor clicks
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        createNotificationChannel()

        initProximitySensor()
        initMediaSessionForEarbuds()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeWord = intent?.getStringExtra(EXTRA_WAKE_WORD) ?: "RIDER"

        try {
            val notification = buildForegroundNotification("RIDER Active: Say 'Varti', 'Rider', 'Buddy' or wave near sensor")
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
            startContinuousListening()
        }

        return START_STICKY
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
                        if (event == null) return
                        val distance = event.values[0]
                        val max = proximitySensor?.maximumRange ?: 5f

                        // Finger/hand is close to phone top sensor
                        if (distance < max || distance <= 3f) {
                            val now = System.currentTimeMillis()
                            if (now - lastTriggerTime > 650) {
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
     * MediaSession: Intercepts physical Bluetooth Earbud touch sensor taps (Play/Pause, Next, Hook)
     * and performs the swipe gesture!
     */
    private fun initMediaSessionForEarbuds() {
        try {
            mediaSession = MediaSession(this, "RiderEarbudsSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setCallback(object : MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }

                        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                            Log.d(TAG, "Real Earbud Touch Sensor clicked: key ${keyEvent.keyCode}! Swiping up...")
                            triggerHapticFeedback()
                            RiderAccessibilityService.instance?.swipeUp()
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }
                })
                isActive = true
            }
            Log.d(TAG, "MediaSession for earbuds touch sensor active")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaSession for earbuds: ${e.message}")
        }
    }

    private fun startContinuousListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                    }

                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        android.os.Handler(mainLooper).postDelayed({
                            startListeningIntent()
                        }, 1000)
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        handleRecognizedText(matches)

                        android.os.Handler(mainLooper).postDelayed({
                            startListeningIntent()
                        }, 400)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        handleRecognizedText(matches)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            startListeningIntent()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing speech recognizer: ${e.message}")
        }
    }

    private fun startListeningIntent() {
        if (speechRecognizer == null) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {}
    }

    /**
     * Checks heard speech for "Varti", "Varti ghe", "Rider", "Buddy", "Swipe"
     */
    private fun handleRecognizedText(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return

        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT)
            Log.d(TAG, "Heard phrase: $lower")

            val isVarti = lower.contains("varti") || lower.contains("var") || lower.contains("वर") || lower.contains("वरती")
            val isRider = lower.contains("rider") || lower.contains("ryder")
            val isBuddy = lower.contains("buddy") || lower.contains("buddie")
            val isSwipe = lower.contains("swipe") || lower.contains("up")

            val matched = when (wakeWord.lowercase(Locale.ROOT)) {
                "varti" -> isVarti || isSwipe || isRider || isBuddy
                "rider" -> isRider || isVarti || isSwipe
                "buddy" -> isBuddy || isVarti || isSwipe
                else -> isVarti || isRider || isBuddy || isSwipe
            }

            if (matched) {
                triggerHapticFeedback()
                val success = RiderAccessibilityService.instance?.swipeUp() ?: false
                Log.d(TAG, "Word matched! Swiped up: $success")
                break
            }
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
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

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
