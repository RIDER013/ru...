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
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.KeyEvent
import java.util.Locale
import kotlin.math.sqrt

/**
 * ==============================================================================
 * VoiceWakeWordService:
 * 1. Voice Recording (AudioRecord & System Mic)
 * 2. Speech Recognition (android.speech.SpeechRecognizer)
 * 3. Voice Command Processing (processVoiceCommand -> swipeUp())
 * 4. Proximity Sensor & Bluetooth Earbud MediaSession
 * ==============================================================================
 */
class VoiceWakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWord = "RIDER"

    // 1. VOICE RECORDING (AudioRecord)
    @Volatile
    private var isAudioRunning = false
    private var audioThread: Thread? = null
    private var audioRecord: AudioRecord? = null

    // 2. SPEECH RECOGNITION (android.speech.SpeechRecognizer)
    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile
    private var isSpeechRecognizing = false
    private var lastCommandTriggerTime = 0L

    // 3. PROXIMITY SENSOR
    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var proximityListener: SensorEventListener? = null

    // 4. BLUETOOTH MEDIASESSION
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
            val notification = buildForegroundNotification("ƦƗƉƐƦ Active: Listening for '$wakeWord' / 'Next'...")
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
            Log.e(TAG, "Notification error: ${e.message}")
        }

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoiceRecordingAndDetection()
        }

        return START_STICKY
    }

    // ==============================================================================
    // 1. VOICE RECORDING & LOW-POWER AUDIO MONITORING (AudioRecord)
    // ==============================================================================
    @SuppressLint("MissingPermission")
    private fun startVoiceRecordingAndDetection() {
        if (isAudioRunning || !isVoiceEnabled) return
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
                    Log.e(TAG, "AudioRecord could not initialize. Using SpeechRecognizer directly.")
                    mainHandler.post { startSpeechRecognitionEngine() }
                    return@Thread
                }

                audioRecord?.startRecording()
                Log.d(TAG, "AudioRecord active: monitoring voice energy...")

                val frame = ShortArray(frameSamples)
                var ambientBaseline = 150.0

                while (isAudioRunning && isVoiceEnabled) {
                    if (isSpeechRecognizing) {
                        Thread.sleep(120)
                        continue
                    }

                    // Ensure recording is active if not in speech recognition
                    if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try {
                            audioRecord?.startRecording()
                        } catch (_: Exception) {
                            Thread.sleep(100)
                            continue
                        }
                    }

                    val readSamples = audioRecord?.read(frame, 0, frame.size) ?: 0
                    if (readSamples <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Calculate Frame Energy (RMS)
                    var sumSq = 0.0
                    for (i in 0 until readSamples) {
                        val sample = frame[i]
                        sumSq += (sample * sample).toDouble()
                    }
                    val frameRms = sqrt(sumSq / readSamples)

                    ambientBaseline = (ambientBaseline * 0.96) + (frameRms * 0.04)
                    val voiceThreshold = maxOf(ambientBaseline * 2.3, 400.0)

                    // Human voice energy spike detected! Trigger speech recognizer burst
                    val now = System.currentTimeMillis()
                    if (frameRms > voiceThreshold && (now - lastCommandTriggerTime > 2500)) {
                        Log.d(TAG, "Voice energy spike (RMS: $frameRms). Freeing mic for speech recognition burst...")
                        // Stop AudioRecord temporarily so SpeechRecognizer gets 100% exclusive access to the microphone
                        try {
                            audioRecord?.stop()
                        } catch (_: Exception) {}
                        mainHandler.post { startSpeechRecognitionEngine() }
                    }

                    Thread.sleep(40)
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord loop error: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                audioRecord = null
            }
        }.apply {
            name = "RiderVoiceRecordingThread"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    // ==============================================================================
    // 2. SPEECH RECOGNITION (android.speech.SpeechRecognizer)
    // ==============================================================================
    private fun startSpeechRecognitionEngine() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer not available on this device.")
            return
        }
        if (isSpeechRecognizing) return
        isSpeechRecognizing = true

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    
                    override fun onEndOfSpeech() {
                        finishSpeechRecognition()
                    }

                    override fun onError(error: Int) {
                        Log.d(TAG, "SpeechRecognizer error: $error")
                        finishSpeechRecognition()
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        handleSpeechResults(matches)
                        finishSpeechRecognition()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        handleSpeechResults(matches)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speechRecognizer?.startListening(intent)

            // Auto-timeout after 1800ms
            mainHandler.postDelayed({
                finishSpeechRecognition()
            }, 1800)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}")
            finishSpeechRecognition()
        }
    }

    private fun finishSpeechRecognition() {
        if (!isSpeechRecognizing) return
        isSpeechRecognizing = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun handleSpeechResults(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return
        for (phrase in matches) {
            Log.d(TAG, "Recognized speech: $phrase")
            val wasExecuted = processVoiceCommand(phrase)
            if (wasExecuted) break
        }
    }

    // ==============================================================================
    // 3. VOICE COMMAND PROCESSING LOGIC (processVoiceCommand -> swipeUp())
    // ==============================================================================
    /**
     * Checks spoken text for keywords: "RIDER", "NEXT", "SWIPE", "UP", "VARTI"
     * Enforces strict 2.5s debounce to prevent double-scrolling!
     * Returns true if command matched and executed.
     */
    fun processVoiceCommand(spokenText: String): Boolean {
        if (!isVoiceEnabled) return false
        val lower = spokenText.lowercase(Locale.ROOT)
        val now = System.currentTimeMillis()

        // 2.5 second cooldown between swipes
        if (now - lastCommandTriggerTime < 2500) {
            return false
        }

        val isRider = lower.contains("rider") || lower.contains("ryder") || lower.contains("raider")
        val isNext = lower.contains("next") || lower.contains("nxt")
        val isSwipe = lower.contains("swipe") || lower.contains("up")
        val isVarti = lower.contains("varti") || lower.contains("var") || lower.contains("वर") || lower.contains("वरती")

        val isMatched = when (wakeWord.uppercase(Locale.ROOT)) {
            "RIDER" -> isRider || isNext || isSwipe
            "NEXT" -> isNext || isRider || isSwipe
            else -> isRider || isNext || isSwipe || isVarti
        }

        if (isMatched) {
            lastCommandTriggerTime = now
            Log.d(TAG, "VOICE COMMAND MATCHED: '$spokenText'! Executing swipeUp()...")
            triggerHapticFeedback()
            val success = RiderAccessibilityService.instance?.swipeUp() ?: false
            Log.d(TAG, "Swipe result: $success")
            return true
        }

        return false
    }

    // ==============================================================================
    // 4. HARDWARE PROXIMITY SENSOR
    // ==============================================================================
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
                                Log.d(TAG, "Proximity wave detected! Swiping up...")
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Proximity init error: ${e.message}")
        }
    }

    // ==============================================================================
    // 5. BLUETOOTH EARBUDS MEDIASESSION ROUTING
    // ==============================================================================
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
                                Log.d(TAG, "Bluetooth earbud tap clicked! Swiping up...")
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
        } catch (e: Exception) {
            Log.e(TAG, "MediaSession setup error: ${e.message}")
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
            .setContentTitle("ƦƗƉƐƦ")
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

        @Volatile
        var isVoiceEnabled: Boolean = true

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
