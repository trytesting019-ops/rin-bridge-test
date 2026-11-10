package my.rinbridge

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Reads only visible ChatGPT accessibility text and reacts only to explicit,
 * allow-listed RIN_BRIDGE commands. The user must enable this service manually.
 */
class RinAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RinBridgeAccess"
        private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
        private const val MARKER_PREFIX = "RIN_BRIDGE:"
    }

    private var lastCommand: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.packageName?.toString() != CHATGPT_PACKAGE) return

        val marker = findCommandMarker(rootInActiveWindow)
        if (marker == null) {
            lastCommand = null
            return
        }

        if (marker == lastCommand) return
        lastCommand = marker
        executeMarker(marker)
    }

    private fun findCommandMarker(root: AccessibilityNodeInfo?): String? {
        if (root == null) return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < 250) {
            val node = queue.removeFirst()
            visited++

            val text = node.text?.toString().orEmpty()
            val description = node.contentDescription?.toString().orEmpty()
            val combined = "$text\n$description"

            val match = Regex("RIN_BRIDGE:(OPEN_WHATSAPP|OPEN_MUSICOLET|PLAY|PAUSE|STOP|NEXT|PREVIOUS)")
                .find(combined.uppercase())

            if (match != null) return match.value

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(queue::addLast)
            }
        }
        return null
    }

    private fun executeMarker(marker: String) {
        Log.i(TAG, "Accepted command marker: $marker")

        when (marker) {
            "RIN_BRIDGE:OPEN_WHATSAPP" -> openPackage("com.whatsapp")
            "RIN_BRIDGE:OPEN_MUSICOLET" -> openPackage("in.krosbits.musicolet")
            "RIN_BRIDGE:PLAY" -> runMusicCommand(RinCommand.PLAY)
            "RIN_BRIDGE:PAUSE" -> runMusicCommand(RinCommand.PAUSE)
            "RIN_BRIDGE:STOP" -> runMusicCommand(RinCommand.STOP)
            "RIN_BRIDGE:NEXT" -> runMusicCommand(RinCommand.NEXT)
            "RIN_BRIDGE:PREVIOUS" -> runMusicCommand(RinCommand.PREVIOUS)
        }
    }

    private fun openPackage(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Log.w(TAG, "Application not installed: $packageName")
                return
            }
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open $packageName", error)
        }
    }

    private fun runMusicCommand(command: RinCommand) {
        MusicController.execute(this, command) { response ->
            Log.i(TAG, response)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }
}
