
package my.rinbridge

import android.Manifest
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
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
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

class VoiceForegroundService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var listening = false
    private var recognizerActive = false
    private var wakeDetected = false
    private val chatExecutor = Executors.newSingleThreadExecutor()
    private val chatHistory = mutableListOf<Pair<String, String>>()

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

        if (command != RinCommand.UNKNOWN) {
            val musicCommands = setOf(
                RinCommand.PLAY, RinCommand.PAUSE, RinCommand.STOP,
                RinCommand.NEXT, RinCommand.PREVIOUS, RinCommand.OPEN_MUSICOLET
            )
            val callback: (String) -> Unit = { response ->
                updateStatus(response)
                wakeDetected = false
            }
            if (command in musicCommands) {
                MusicController.execute(this, command, callback)
            } else {
                DeviceActionController.execute(this, command, callback)
            }
            return
        }

        // Arahan biasa dihantar kepada Gemini melalui endpoint /v1/chat.
        updateStatus("RIN SEDANG MEMPROSES: $text")
        chatExecutor.execute {
            try {
                val prefs = getSharedPreferences("rinbridge_relay", MODE_PRIVATE)
                val baseUrl = prefs.getString("relay_url", "")?.trim()?.trimEnd('/') ?: ""
                val token = prefs.getString("device_token", "")?.trim() ?: ""

                if (!baseUrl.startsWith("https://") || token.length < 24) {
                    updateStatus("SAMBUNGAN AI BELUM DISEDIAKAN: masukkan URL relay dan Device Token")
                    wakeDetected = false
                    return@execute
                }

                val messages = JSONArray()
                synchronized(chatHistory) {
                    chatHistory.forEach { item ->
                        messages.put(JSONObject().put("role", item.first).put("text", item.second))
                    }
                    messages.put(JSONObject().put("role", "user").put("text", text))
                }

                val connection = (URL("$baseUrl/v1/chat").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 30000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")

                    val payload = JSONObject().put("messages", messages).toString()
                    connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                    val result = JSONObject(body)

                    if (code !in 200..299) {
                        val detail = result.optString("message", result.optString("error", "HTTP $code"))
                        updateStatus("AI GAGAL ($code): $detail")
                        return@execute
                    }

                    val reply = result.optString("reply").trim()
                    if (reply.isBlank()) {
                        updateStatus("AI MEMULANGKAN JAWAPAN KOSONG")
                        return@execute
                    }

                    synchronized(chatHistory) {
                        chatHistory.add("user" to text)
                        chatHistory.add("model" to reply)
                        while (chatHistory.size > 12) chatHistory.removeAt(0)
                    }
                    updateStatus("RIN: $reply")
                    speakWithSulafat(reply, baseUrl, token)
                } finally {
                    connection.disconnect()
                }
            } catch (error: Exception) {
                Log.e(TAG, "Chat request failed", error)
                updateStatus("GAGAL HUBUNGI AI: ${error.message ?: "ralat rangkaian"}")
            } finally {
                wakeDetected = false
            }
        }
    }

    private fun speakWithSulafat(text: String, baseUrl: String, token: String) {
        try {
            updateStatus("RIN SEDANG MENYEDIAKAN SUARA SULAFAT")
            val connection = URL(baseUrl + "/v1/tts").openConnection() as HttpURLConnection
            val audioBytes: ByteArray
            val mimeType: String
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 10000
                connection.readTimeout = 45000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer " + token)
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                val payload = JSONObject().put("text", text).toString()
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                val result = JSONObject(body)
                if (code !in 200..299) {
                    updateStatus("SULAFAT GAGAL (" + code + "): " + result.optString("message", result.optString("error", "ralat TTS")))
                    return
                }
                val encoded = result.optString("audio")
                if (encoded.isBlank()) { updateStatus("SULAFAT MEMULANGKAN AUDIO KOSONG"); return }
                audioBytes = Base64.decode(encoded, Base64.DEFAULT)
                mimeType = result.optString("mimeType", "audio/L16;rate=24000")
            } finally { connection.disconnect() }
            val rateMatch = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE).find(mimeType)
            val sampleRate = rateMatch?.groupValues?.get(1)?.toIntOrNull() ?: 24000
            val minBuffer = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0 || audioBytes.isEmpty()) { updateStatus("FORMAT AUDIO SULAFAT TIDAK DISOKONG"); return }
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(maxOf(minBuffer, audioBytes.size))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                updateStatus("RIN SEDANG BERCAKAP - SULAFAT")
                track.write(audioBytes, 0, audioBytes.size)
                track.play()
                while (track.playState == AudioTrack.PLAYSTATE_PLAYING && track.playbackHeadPosition < audioBytes.size / 2) { Thread.sleep(40) }
            } finally { track.stop(); track.release() }
            updateStatus("RIN SELESAI BERCAKAP")
        } catch (error: Exception) {
            Log.e(TAG, "Sulafat TTS failed", error)
            updateStatus("RALAT SUARA SULAFAT: " + (error.message ?: "ralat audio"))
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
        // Keep the microphone paused until Gemini has replied and Sulafat has finished speaking.
        if (!listening || wakeDetected) return

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
