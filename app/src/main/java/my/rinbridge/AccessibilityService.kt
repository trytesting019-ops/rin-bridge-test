package my.rinbridge

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper

class AccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Monitor foreground application
        val packageName = event.packageName?.toString() ?: return

        // Handle window changes
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                android.util.Log.d(
                    "RinBridge",
                    "Opened app: $packageName"
                )
            }

            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                android.util.Log.d(
                    "RinBridge",
                    "View clicked: $packageName"
                )
            }
        }
    }

    override fun onInterrupt() {
        android.util.Log.d("RinBridge", "Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        android.util.Log.d(
            "RinBridge",
            "Accessibility Service connected"
        )
    }

    fun openApplication(packageName: String): Boolean {
        return try {
            val intent = packageManager
                .getLaunchIntentForPackage(packageName)

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}
