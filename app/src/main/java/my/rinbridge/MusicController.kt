
package my.rinbridge

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

object MusicController {

    private const val MUSICOLET_PACKAGE =
        "in.krosbits.musicolet"

    fun execute(
        context: Context,
        command: RinCommand,
        callback: (String) -> Unit
    ) {
        when (command) {

            RinCommand.OPEN_MUSICOLET -> {
                openMusicolet(context)
                callback("Opening Musicolet")
            }

            RinCommand.PLAY -> {
                openMusicolet(context)

                Handler(Looper.getMainLooper()).postDelayed({
                    sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
                    callback("Play command sent")
                }, 1000)
            }

            RinCommand.PAUSE -> {
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
                callback("Pause command sent")
            }

            RinCommand.STOP -> {
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_STOP)
                callback("Stop command sent")
            }

            RinCommand.NEXT -> {
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                callback("Next song command sent")
            }

            RinCommand.PREVIOUS -> {
                sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                callback("Previous song command sent")
            }

            RinCommand.UNKNOWN -> {
                callback("Command not recognized")
            }
        }
    }

    private fun openMusicolet(context: Context) {
        val launchIntent =
            context.packageManager.getLaunchIntentForPackage(
                MUSICOLET_PACKAGE
            )

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    }

    private fun sendMediaKey(
        context: Context,
        keyCode: Int
    ) {
        val audioManager =
            context.getSystemService(Context.AUDIO_SERVICE)
                    as AudioManager

        audioManager.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        )

        audioManager.dispatchMediaKeyEvent(
            KeyEvent(KeyEvent.ACTION_UP, keyCode)
        )
    }
}
