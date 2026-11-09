
package my.rinbridge

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

class VoiceForegroundService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var listening = false
    private var wakeDetected = false

    companion object {
        const val CHANNEL_ID = "rin_voice_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STATUS = "my.rinbridge.RIN_STATUS"
        const val EXTRA_STATUS = "status"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        startForeground(
            NOTIFICATION_ID,
            buildNotification("Rin sedang bersedia")
        )

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateStatus("Speech recognition tidak tersedia")
            stopSelf()
            return START_NOT_STICKY
        }

        if (speechRecognizer == null) {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(this)
        }

        listening = true
        startListening()

        return START_STICKY
    }

    private fun startListening() {
        if (!listening) return

        val intent = Intent(
            RecognizerIntent.ACTION_RECOGNIZE_SPEECH
        ).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "ms-MY"
            )
            putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true
            )
            putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )
        }

        try {
            speechRecognizer?.startListening(intent)
            updateStatus("HEARING ON")
        } catch (e: Exception) {
            Log.e("RinBridge", "Start listening failed", e)
            restartListening()
        }
    }

    override fun onResults(results: Bundle?) {

        val matches = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )

        val spoken = matches?.firstOrNull()
            ?.lowercase(Locale.ROOT) ?: ""

        Log.d("RinBridge", "Heard: $spoken")

        val hasWakeWord =
            spoken.contains("hey rin") ||
            spoken.contains("hei rin") ||
            spoken.contains("hai rin")

        if (hasWakeWord) {
            wakeDetected = true
            updateStatus("HEY RIN DETECTED")

            val commandText = spoken
                .replace(
                    Regex("\\b(hey|hei|hai)\\s+rin\\b"),
                    ""
                )
                .trim()

            if (commandText.isNotEmpty()) {
                processCommand(commandText)
            }

        } else if (wakeDetected && spoken.isNotBlank()) {
            processCommand(spoken)
        }

        restartListening()
    }

    private fun processCommand(text: String) {

        val command = CommandRouter.parse(text)

        if (command == RinCommand.UNKNOWN) {
            updateStatus("Arahan tidak dikenali: $text")
            return
        }

        MusicController.execute(
            this,
            command
        ) { response ->

            updateStatus(response)
            wakeDetected = false
        }
    }

    override fun onError(error: Int) {
        Log.e("RinBridge", "Speech error: $error")
        restartListening()
    }

    private fun restartListening() {
        if (!listening) return

        handler.removeCallbacksAndMessages(null)

        handler.postDelayed({
            if (listening) {
                startListening()
            }
        }, 700)
    }

    private fun updateStatus(status: String) {

        val intent = Intent(ACTION_STATUS).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATUS, status)
        }

        sendBroadcast(intent)

        val manager =
            getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(status)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rin Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NOTIFICATION_SERVICE)
                    as NotificationManager

            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(
        status: String
    ): Notification {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RinBridge")
                .setContentText(status)
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RinBridge")
                .setContentText(status)
                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}
}
