package com.rider.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
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
import java.util.Locale

class VoiceWakeWordService : Service() {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var wakeWord = "RIDER"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeWord = intent?.getStringExtra(EXTRA_WAKE_WORD) ?: "RIDER"

        val notification = buildForegroundNotification("Listening for '$wakeWord' in background...")
        startForeground(NOTIFICATION_ID, notification)

        startContinuousListening()
        return START_STICKY
    }

    private fun startContinuousListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e(TAG, "Speech recognition unavailable")
            return
        }

        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                    Log.d(TAG, "Speech error code: $error. Restarting listener...")
                    // Restart after slight pause to prevent spin-loop
                    android.os.Handler(mainLooper).postDelayed({
                        startListeningIntent()
                    }, 800)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    handleRecognizedText(matches)

                    // Restart listening for continuous wake-word detection
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
    }

    private fun startListeningIntent() {
        if (speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening: ${e.message}")
        }
    }

    private fun handleRecognizedText(matches: List<String>?) {
        if (matches.isNullOrEmpty()) return

        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT)
            Log.d(TAG, "Heard: $lower (Target: $wakeWord)")

            val target = wakeWord.lowercase(Locale.ROOT)
            val matched = when (target) {
                "rider" -> lower.contains("rider") || lower.contains("ryder") || lower.contains("swipe")
                "buddy" -> lower.contains("buddy") || lower.contains("buddie") || lower.contains("swipe")
                else -> lower.contains(target)
            }

            if (matched) {
                triggerHapticFeedback()
                val success = RiderAccessibilityService.instance?.swipeUp() ?: false
                Log.d(TAG, "Wake word matched! Swiping up, result: $success")
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rider Assistant Background Voice",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps voice wake-word active in the background"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
            .setContentTitle("Rider Assistant")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoiceWakeWordService"
        private const val NOTIFICATION_ID = 42001
        private const val CHANNEL_ID = "rider_voice_channel"
        const val EXTRA_WAKE_WORD = "extra_wake_word"

        fun start(context: Context, wakeWord: String = "RIDER") {
            val intent = Intent(context, VoiceWakeWordService::class.java).apply {
                putExtra(EXTRA_WAKE_WORD, wakeWord)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VoiceWakeWordService::class.java)
            context.stopService(intent)
        }
    }
}
