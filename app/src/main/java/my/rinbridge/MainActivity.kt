package my.rinbridge

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

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

        val accessibilityButton = Button(this).apply {
            text = "Enable Accessibility"
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
        layout.addView(accessibilityButton)
        layout.addView(testButton)

        setContentView(layout)
    }
}
