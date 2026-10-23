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
import android.os.PowerManager
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
 * 1. Continuous Voice Recording (AudioRecord & System Mic) with Watchdog & WakeLock
 * 2. Speech Recognition (android.speech.SpeechRecognizer)
 * 3. Voice Command Processing (processVoiceCommand -> swipeUp())
 * 4. Proximity Sensor & Bluetooth Earbud MediaSession
 * ==============================================================================
 */
class VoiceWakeWordService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var wakeWord = "RIDER"

    // CPU WakeLock: Prevents Android Doze / Screen-Off sleep from turning off the mic
    private var wakeLock: PowerManager.WakeLock? = null

    // 1. VOICE RECORDING (AudioRecord) & CONTINUOUS WATCHDOG
    @Volatile
    private var isAudioRunning = false
    private var audioThread: Thread? = null
    private var audioRecord: AudioRecord? = null
    @Volatile
    private var lastAudioFrameTimestamp = 0L

    // Watchdog Timer: Runs every 5s, restarts mic loop if stalled or interrupted
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning && isVoiceEnabled) {
                checkAndReviveAudioRecording()
            }
            watchdogHandler.postDelayed(this, 5000)
        }
    }

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

        // Acquire WakeLock so background mic stays continuously active even when screen is locked
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RiderAssistant:VoiceWakeLock")?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours max safety
            }
            Log.d(TAG, "Acquired PARTIAL_WAKE_LOCK for continuous mic capture")
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error: ${e.message}")
        }

        initProximitySensor()
        if (isBluetoothEnabled) {
            initMediaSessionForEarbuds()
        }

        // Start continuous watchdog
        watchdogHandler.postDelayed(watchdogRunnable, 5000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeWord = intent?.getStringExtra(EXTRA_WAKE_WORD) ?: "RIDER"

        try {
            val notification = buildForegroundNotification("ƦƗƉƐƦ Active: Continuous mic listening for '$wakeWord' / 'Next'...")
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
    private synchronized fun startVoiceRecordingAndDetection() {
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
                    Log.e(TAG, "AudioRecord could not initialize. Will retry via watchdog.")
                    isAudioRunning = false
                    return@Thread
                }

                audioRecord?.startRecording()
                lastAudioFrameTimestamp = System.currentTimeMillis()
                Log.d(TAG, "AudioRecord active: continuous voice energy monitoring running...")

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
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to startRecording: ${e.message}")
                            Thread.sleep(100)
                            continue
                        }
                    }

                    val readSamples = audioRecord?.read(frame, 0, frame.size) ?: 0
                    if (readSamples <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Update timestamp to confirm mic is actively capturing live audio frames
                    lastAudioFrameTimestamp = System.currentTimeMillis()

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
                isAudioRunning = false
            }
        }.apply {
            name = "RiderVoiceRecordingThread"
            priority = Thread.NORM_PRIORITY
            start()
        }
    }

    /**
     * Continuous Watchdog: checks if the mic thread died or has stalled for > 8s,
     * and automatically revives the audio recording loop so the mic NEVER turns off.
     */
    private fun checkAndReviveAudioRecording() {
        val now = System.currentTimeMillis()
        val isStalled = (lastAudioFrameTimestamp > 0 && (now - lastAudioFrameTimestamp > 8000))
        val isDead = (audioThread == null || !audioThread!!.isAlive)

        if ((isStalled || isDead) && !isSpeechRecognizing) {
            Log.w(TAG, "Watchdog: Mic stalled or thread dead (stalled: $isStalled, dead: $isDead). Reviving mic immediately...")
            restartVoiceRecording()
        }
    }

    private synchronized fun restartVoiceRecording() {
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

        lastAudioFrameTimestamp = System.currentTimeMillis()
        mainHandler.postDelayed({
            if (isServiceRunning && isVoiceEnabled) {
                startVoiceRecordingAndDetection()
            }
        }, 300)
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
                        Log.d(TAG, "SpeechRecognizer error: $error. Resuming continuous mic...")
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

        // Immediately update timestamp & resume continuous mic recording
        lastAudioFrameTimestamp = System.currentTimeMillis()
        try {
            if (audioRecord != null && audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.startRecording()
            } else if (audioRecord == null) {
                restartVoiceRecording()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error re-activating audio record: ${e.message}. Restarting...")
            restartVoiceRecording()
        }
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

            // Request AudioFocus so Bluetooth earbud media buttons are routed directly to Rider Assistant
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = android.media.AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(true)
                    .build()
                audioManager?.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }

            mediaSession = MediaSession(this, "RiderEarbudsActiveSession").apply {
                setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
                setPlaybackState(
                    PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0L, 1.0f)
                        .setActions(
                            PlaybackState.ACTION_PLAY or
                            PlaybackState.ACTION_PAUSE or
                            PlaybackState.ACTION_PLAY_PAUSE or
                            PlaybackState.ACTION_SKIP_TO_NEXT or
                            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                            PlaybackState.ACTION_FAST_FORWARD or
                            PlaybackState.ACTION_REWIND or
                            PlaybackState.ACTION_STOP
                        )
                        .build()
                )

                setCallback(object : MediaSession.Callback() {
                    private var lastEarbudClickTime = 0L

                    private fun handleEarbudGesture(source: String) {
                        val now = System.currentTimeMillis()
                        if (now - lastEarbudClickTime > 400) {
                            lastEarbudClickTime = now
                            Log.d(TAG, "Bluetooth earbud touch triggered via $source! Swiping up...")
                            triggerHapticFeedback()
                            RiderAccessibilityService.instance?.swipeUp()
                        }
                    }

                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }

                        if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                            handleEarbudGesture("MediaButtonEvent key=${keyEvent.keyCode}")
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    override fun onPlay() { handleEarbudGesture("onPlay") }
                    override fun onPause() { handleEarbudGesture("onPause") }
                    override fun onSkipToNext() { handleEarbudGesture("onSkipToNext") }
                    override fun onSkipToPrevious() { handleEarbudGesture("onSkipToPrevious") }
                    override fun onFastForward() { handleEarbudGesture("onFastForward") }
                    override fun onRewind() { handleEarbudGesture("onRewind") }
                    override fun onStop() { handleEarbudGesture("onStop") }
                })
                isActive = true
            }
            Log.d(TAG, "Bluetooth Earbuds MediaSession initialized and active")
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

        // Stop watchdog
        try {
            watchdogHandler.removeCallbacks(watchdogRunnable)
        } catch (_: Exception) {}

        // Release WakeLock
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released PARTIAL_WAKE_LOCK")
            }
        } catch (_: Exception) {}
        wakeLock = null

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
