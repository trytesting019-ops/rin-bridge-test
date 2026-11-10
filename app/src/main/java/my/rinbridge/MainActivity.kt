
package my.rinbridge

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.provider.Settings
import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.*
import android.animation.ObjectAnimator
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.animation.AccelerateDecelerateInterpolator

class MainActivity : Activity() {

    private val microphoneRequestCode = 1001

    private lateinit var statusText: TextView
    private lateinit var avatar: ImageView

    private var avatarAnimation: AnimatorSet? = null
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent?.action == VoiceForegroundService.ACTION_STATUS) {

                val status = intent.getStringExtra(
                    VoiceForegroundService.EXTRA_STATUS
                ) ?: return

                runOnUiThread {
                    statusText.text = "Status: $status"

                    if (status == "HEY RIN DETECTED") {
                        Toast.makeText(
                            this@MainActivity,
                            "Rin dengar panggilan sayang!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(30, 30, 30, 30)
            setBackgroundColor(
                android.graphics.Color.rgb(5, 12, 35)
            )
        }

        val title = TextView(this).apply {
            text = "RINBRIDGE AI"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.CYAN)
        }

        val description = TextView(this).apply {
            text = "Your Personal Android Assistant"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.WHITE)
        }

        avatar = ImageView(this).apply {
            setImageResource(R.drawable.rin_avatar)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Rin AI Avatar"
        }

        layout.addView(
            avatar,
            LinearLayout.LayoutParams(280, 330).apply {
                setMargins(0, 20, 0, 15)
            }
        )

        statusText = TextView(this).apply {
            text = "Status: Hey Rin OFF"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(android.graphics.Color.CYAN)
            setPadding(0, 15, 0, 25)
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
        layout.addView(testButton)

        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()

        if (!receiverRegistered) {
            val filter = IntentFilter(
                VoiceForegroundService.ACTION_STATUS
            )

            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    statusReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(statusReceiver, filter)
            }

            receiverRegistered = true
        }
    }

    override fun onPause() {
        if (receiverRegistered) {
            unregisterReceiver(statusReceiver)
            receiverRegistered = false
        }

        super.onPause()
    }

    private fun animateAvatar() {
        avatarAnimation?.cancel()

        val scaleX = ObjectAnimator.ofFloat(
            avatar, View.SCALE_X, 1f, 1.04f
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        val scaleY = ObjectAnimator.ofFloat(
            avatar, View.SCALE_Y, 1f, 1.04f
        ).apply {
            duration = 900
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }

        avatarAnimation = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopAvatarAnimation() {
        avatarAnimation?.cancel()
        avatarAnimation = null
        avatar.scaleX = 1f
        avatar.scaleY = 1f
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

            statusText.text = "Status: HEARING ON"
            animateAvatar()

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
        stopAvatarAnimation()
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
            if (
                grantResults.isNotEmpty() &&
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

    override fun onDestroy() {
        stopAvatarAnimation()
        super.onDestroy()
    }
}
