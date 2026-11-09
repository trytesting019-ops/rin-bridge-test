
package my.rinbridge

import android.Manifest
import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
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
    private var recognizerActive = false
    private var wakeDetected = false

    private val restartRunnable = Runnable {
        if (listening && !recognizerActive) {
            startListening()
        }
    }

    companion object {
        const val CHANNEL_ID = "rin_voice_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STATUS = "my.rinbridge.RIN_STATUS"
        const val EXTRA_STATUS = "status"

        private const val TAG = "RinBridge"
        private const val RESTART_DELAY = 1500L
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

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            updateStatus("Kebenaran mikrofon tiada")
            stopSelf()
            return START_NOT_STICKY
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateStatus("Speech Recognition tidak tersedia")
            stopSelf()
            return START_NOT_STICKY
        }

        if (speechRecognizer == null) {
            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)

            speechRecognizer?.setRecognitionListener(this)
        }

        if (!listening) {
            listening = true
            startListening()
        }

        return START_STICKY
    }

    private fun startListening() {
        if (!listening || recognizerActive) return

        val recognizer = speechRecognizer ?: return

        val speechIntent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                // Biarkan Android menggunakan bahasa lalai.
                // Jangan paksa en-US atau ms-MY dahulu.

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
            updateStatus("MENYAMBUNG MIKROFON...")
            recognizerActive = true
            recognizer.startListening(speechIntent)

        } catch (e: Exception) {
            recognizerActive = false

            Log.e(TAG, "Start listening failed", e)

            updateStatus("GAGAL MULA: ${e.message}")

            scheduleRestart()
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {
        updateStatus("MIKROFON SIAP - CAKAP SEKARANG")
    }

    override fun onBeginningOfSpeech() {
        updateStatus("SUARA DIKESAN")
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )

        val spoken = matches?.firstOrNull() ?: ""

        if (spoken.isNotBlank()) {
            updateStatus("DENGAR: $spoken")
        }
    }

    override fun onResults(results: Bundle?) {
        recognizerActive = false

        val matches = results?.getStringArrayList(
            SpeechRecognizer.RESULTS_RECOGNITION
        )

        val spoken = matches?.firstOrNull()
            ?.lowercase(Locale.ROOT) ?: ""

        Log.d(TAG, "Heard: $spoken")

        if (spoken.isBlank()) {
            updateStatus("TIADA SUARA DIKENALI")
            scheduleRestart()
            return
        }

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

        } else if (wakeDetected) {
            processCommand(spoken)

        } else {
            updateStatus("SUARA DITERIMA: $spoken")
        }

        scheduleRestart()
    }

    private fun processCommand(text: String) {
        val command = CommandRouter.parse(text)

        if (command == RinCommand.UNKNOWN) {
            updateStatus("ARAHAN TIDAK DIKENALI: $text")
            wakeDetected = false
            return
        }

        MusicController.execute(this, command) { response ->
            updateStatus(response)
            wakeDetected = false
        }
    }

    override fun onError(error: Int) {
        recognizerActive = false

        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "RALAT AUDIO"

            SpeechRecognizer.ERROR_CLIENT ->
                "RALAT CLIENT"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "KEBENARAN MIKROFON TIADA"

            SpeechRecognizer.ERROR_NETWORK ->
                "RALAT NETWORK"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "NETWORK TIMEOUT"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "SUARA TIDAK DIKENALI"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "SPEECH RECOGNIZER BUSY"

            SpeechRecognizer.ERROR_SERVER ->
                "RALAT SERVER"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "TIADA SUARA DITERIMA"

            12 ->
                "BAHASA TIDAK DISOKONG"

            13 ->
                "BAHASA BELUM TERSEDIA"

            else ->
                "RALAT SPEECH: $error"
        }

        Log.e(TAG, "$message ($error)")
        updateStatus(message)

        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            recreateRecognizer()
        } else {
            scheduleRestart()
        }
    }

    private fun scheduleRestart() {
        if (!listening) return

        handler.removeCallbacks(restartRunnable)
        handler.postDelayed(
            restartRunnable,
            RESTART_DELAY
        )
    }

    private fun recreateRecognizer() {
        handler.removeCallbacks(restartRunnable)

        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Destroy recognizer failed", e)
        }

        speechRecognizer = null
        recognizerActive = false

        handler.postDelayed({
            if (listening) {
                speechRecognizer =
                    SpeechRecognizer.createSpeechRecognizer(this)

                speechRecognizer?.setRecognitionListener(this)

                startListening()
            }
        }, 2000)
    }

    private fun updateStatus(status: String) {
        Log.d(TAG, status)

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

    private fun buildNotification(status: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RinBridge")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()

        } else {

            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RinBridge")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        listening = false
        recognizerActive = false

        handler.removeCallbacksAndMessages(null)

        speechRecognizer?.destroy()
        speechRecognizer = null

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {}

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
