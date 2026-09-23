package com.example.hologramdemo

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private var isVisionOn = false
    private var isGamingModeOn = false
    private var previousDNDMode = NotificationManager.INTERRUPTION_FILTER_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusText = findViewById<TextView>(R.id.status)
        val visionButton = findViewById<Button>(R.id.visionButton)
        val gamingModeButton = findViewById<Button>(R.id.gamingModeButton)

        if (!Settings.canDrawOverlays(this)) {
            askOverlayPermission()
        }

        visionButton.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                askOverlayPermission()
                return@setOnClickListener
            }

            isVisionOn = !isVisionOn
            if (isVisionOn) {
                startService(Intent(this, VisionOverlayService::class.java))
                visionButton.text = "Disable Vision Filter"
                statusText.text = "Vision: ON (Green + Edge Block)"
            } else {
                stopService(Intent(this, VisionOverlayService::class.java))
                visionButton.text = "Enable Vision Filter"
                statusText.text = "Vision: OFF"
            }
        }

        gamingModeButton.setOnClickListener {
            isGamingModeOn = !isGamingModeOn

            if (isGamingModeOn) {
                enableGamingMode()
                gamingModeButton.text = "Exit Gaming Mode"
            } else {
                disableGamingMode()
                gamingModeButton.text = "Enable Gaming Mode"
            }
        }
    }

    private fun askOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun enableGamingMode() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                previousDNDMode = notificationManager.currentInterruptionFilter
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                Toast.makeText(this, "Gaming Mode ON: Calls Blocked", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "Please allow DND access for Gaming Mode", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun disableGamingMode() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(previousDNDMode)
                Toast.makeText(this, "Gaming Mode OFF: Notifications Restored", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
