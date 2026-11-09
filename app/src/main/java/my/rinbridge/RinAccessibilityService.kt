package my.rinbridge

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class RinAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Rin akan memproses interaksi aplikasi di sini
    }

    override fun onInterrupt() {
        // Dipanggil apabila servis terganggu
    }
}