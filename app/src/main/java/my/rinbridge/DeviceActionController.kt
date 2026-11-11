package my.rinbridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings

/**
 * Safe Android actions exposed through Rin voice commands.
 * Calls, messages and edits are opened for the user to review, never sent silently.
 */
object DeviceActionController {
    fun execute(context: Context, command: RinCommand, callback: (String) -> Unit) {
        val intent = try {
            when (command) {
                RinCommand.OPEN_CAMERA -> Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                RinCommand.OPEN_MAPS ->
                    context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                        ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
                RinCommand.OPEN_CALENDAR ->
                    Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis()))
                RinCommand.OPEN_CLOCK -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
                RinCommand.OPEN_SETTINGS -> Intent(Settings.ACTION_SETTINGS)
                RinCommand.OPEN_DIALER -> Intent(Intent.ACTION_DIAL)
                RinCommand.OPEN_MESSAGES -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
                RinCommand.OPEN_WHATSAPP ->
                    context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                        ?: throw IllegalStateException("WhatsApp belum dipasang")
                else -> {
                    callback("Arahan peranti tidak disokong")
                    return
                }
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (error: Exception) {
            callback(error.message ?: "Tidak dapat menyediakan aplikasi")
            return
        }

        try {
            context.startActivity(intent)
            callback(
                when (command) {
                    RinCommand.OPEN_CAMERA -> "Membuka kamera"
                    RinCommand.OPEN_MAPS -> "Membuka peta"
                    RinCommand.OPEN_CALENDAR -> "Membuka kalendar"
                    RinCommand.OPEN_CLOCK -> "Membuka jam dan penggera"
                    RinCommand.OPEN_SETTINGS -> "Membuka tetapan telefon"
                    RinCommand.OPEN_DIALER -> "Membuka telefon"
                    RinCommand.OPEN_MESSAGES -> "Membuka mesej"
                    RinCommand.OPEN_WHATSAPP -> "Membuka WhatsApp"
                    else -> "Arahan selesai"
                }
            )
        } catch (error: Exception) {
            callback("Aplikasi tidak dapat dibuka: " + (error.message ?: "ralat"))
        }
    }
}
