package my.rinbridge

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground HTTPS polling client for the RinBridge relay.
 * The device token is stored locally in private app preferences.
 */
class RelayForegroundService : Service() {

    companion object {
        const val ACTION_STATUS = "my.rinbridge.RELAY_STATUS"
        const val EXTRA_STATUS = "status"
        private const val TAG = "RinRelay"
        private const val CHANNEL_ID = "rin_relay_channel"
        private const val NOTIFICATION_ID = 202
        private const val DEVICE_ID = "doogee-s200-plus"
        private const val POLL_INTERVAL_MS = 5000L
    }

    private val running = AtomicBoolean(false)
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Menyediakan sambungan"))
        if (running.get()) return START_STICKY

        val prefs = getSharedPreferences("rinbridge_relay", MODE_PRIVATE)
        val baseUrl = prefs.getString("relay_url", "")?.trim()?.trimEnd('/') ?: ""
        val deviceToken = prefs.getString("device_token", "")?.trim() ?: ""

        if (!baseUrl.startsWith("https://") || deviceToken.length < 24) {
            broadcastStatus("Tetapan pelayan atau token belum sah")
            stopSelf()
            return START_NOT_STICKY
        }

        running.set(true)
        worker = Thread({
            broadcastStatus("Menyambung ke relay")
            while (running.get() && !Thread.currentThread().isInterrupted) {
                try {
                    val commands = fetchCommands(baseUrl, deviceToken)
                    for (i in 0 until commands.length()) {
                        val item = commands.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        val command = item.optString("command")
                        if (id.isNotBlank() && executeCommand(command)) {
                            acknowledge(baseUrl, deviceToken, id)
                        }
                    }
                    broadcastStatus("Relay aktif")
                } catch (error: Exception) {
                    Log.e(TAG, "Polling failed", error)
                    broadcastStatus("Relay terputus: semak internet atau token")
                }
                try {
                    Thread.sleep(POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "RinBridgeRelayPoller").also { it.start() }

        return START_STICKY
    }

    private fun fetchCommands(baseUrl: String, token: String): org.json.JSONArray {
        val encoded = URLEncoder.encode(DEVICE_ID, "UTF-8")
        val connection = (URL("$baseUrl/v1/next?device_id=$encoded").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Relay HTTP $code")
            return JSONObject(body).optJSONArray("commands") ?: org.json.JSONArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun executeCommand(command: String): Boolean {
        return try {
            when (command) {
                "OPEN_WHATSAPP" -> {
                    val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
                        ?: return false
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launch)
                    true
                }
                "OPEN_MUSICOLET" -> {
                    MusicController.execute(this, RinCommand.OPEN_MUSICOLET) { }
                    true
                }
                "PLAY" -> { MusicController.execute(this, RinCommand.PLAY) { }; true }
                "PAUSE" -> { MusicController.execute(this, RinCommand.PAUSE) { }; true }
                "STOP" -> { MusicController.execute(this, RinCommand.STOP) { }; true }
                "NEXT" -> { MusicController.execute(this, RinCommand.NEXT) { }; true }
                "PREVIOUS" -> { MusicController.execute(this, RinCommand.PREVIOUS) { }; true }
                else -> false
            }
        } catch (error: Exception) {
            Log.e(TAG, "Command failed: $command", error)
            false
        }
    }

    private fun acknowledge(baseUrl: String, token: String, id: String) {
        val connection = (URL("$baseUrl/v1/ack").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("device_id", DEVICE_ID).put("id", id).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) Log.w(TAG, "Ack returned HTTP $code")
        } finally {
            connection.disconnect()
        }
    }

    private fun broadcastStatus(status: String) {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName).putExtra(EXTRA_STATUS, status))
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification(status))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "RinBridge Relay", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(status: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("RinBridge")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RinBridge")
                .setContentText(status)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        running.set(false)
        worker?.interrupt()
        worker = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
