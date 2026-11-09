
package my.rinbridge

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val microphoneRequestCode = 1001

    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "RinBridge AI"
            textSize = 28f
            gravity = Gravity.CENTER
        }

        val description = TextView(this).apply {
            text = "Your personal Android assistant"
            textSize = 16f
            gravity = Gravity.CENTER
        }

        statusText = TextView(this).apply {
            text = "Status: Hey Rin OFF"
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 30, 0, 30)
        }

        val startButton = Button(this).apply {
            text = "Hidupkan Hey Rin"
            setOnClickListener {
                startVoiceAssistant()
            }
        }

        val stopButton = Button(this).apply {
            text = "Matikan Hey Rin"
            setOnClickListener {
                stopVoiceAssistant()
            }
        }

        val accessibilityButton = Button(this).apply {
            text = "Buka Accessibility Settings"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        }

        val testButton = Button(this).apply {
            text = "Test RinBridge"
            setOnClickListener {
                Toast.makeText(
                    this@MainActivity,
                    "RinBridge is running!",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        layout.addView(title)
        layout.addView(description)
        layout.addView(statusText)
        layout.addView(startButton)
        layout.addView(stopButton)
        layout.addView(accessibilityButton)
        layout.addView(testButton)

        setContentView(layout)
    }

    private fun startVoiceAssistant() {

        if (checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                microphoneRequestCode
            )
            return
        }

        try {
            val intent = Intent(
                this,
                VoiceForegroundService::class.java
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            statusText.text = "Status: Voice Service ON"

            Toast.makeText(
                this,
                "Rin Voice Service started",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopVoiceAssistant() {

        val intent = Intent(
            this,
            VoiceForegroundService::class.java
        )

        stopService(intent)

        statusText.text = "Status: Hey Rin OFF"

        Toast.makeText(
            this,
            "Rin Voice Service stopped",
            Toast.LENGTH_SHORT
        ).show()
    }

    @Deprecated("Deprecated in Android API")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == microphoneRequestCode) {

            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceAssistant()
            } else {
                Toast.makeText(
                    this,
                    "Microphone permission is required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
