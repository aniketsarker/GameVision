package com.example.hologramdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BubbleService : Service() {

    companion object { var currentGame: String? = null }

    private var wm: WindowManager? = null
    private var bubbleView: TextView? = null
    private var panelView: LinearLayout? = null
    private var panelOpen = false
    private var dndOn = false
    private var usageAsked = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var games: Set<String>

    private val extraGames = setOf(
        "com.dts.freefireth", "com.dts.freefiremax", "com.pubg.imobile",
        "com.tencent.ig", "com.activision.callofduty.shooter",
        "com.mobile.legends", "com.supercell.clashofclans"
    )

    private val pollRunnable = object : Runnable {
        override fun run() { updateBubble(); handler.postDelayed(this, 1500) }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        games = try {
            packageManager.getInstalledApplications(0)
                .filter { it.category == ApplicationInfo.CATEGORY_GAME }
                .map { it.packageName }.toSet() + extraGames
        } catch (e: Exception) { extraGames }
        startForegroundNotif()
        handler.post(pollRunnable)
    }

    private fun startForegroundNotif() {
        val ch = NotificationChannel("bubble", "Game Bubble", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "bubble")
            .setContentTitle("GameVision Bubble active")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
    }

    private fun hasUsageAccess(): Boolean = try {
        packageManager.getApplicationInfo(packageName, 0).flags and
                ApplicationInfo.FLAG_GRANTED_USAGE_ACCESS != 0
    } catch (e: Exception) { false }

    private fun foregroundPackage(): String? {
        if (!hasUsageAccess()) return null
        val usm = getSystemService(UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - 10000, end)
        val ev = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                ev.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = ev.packageName
        }
        return last
    }

    private fun updateBubble() {
        val fg = foregroundPackage()
        if (fg == null) {
            if (!usageAsked) {
                usageAsked = true
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            showBubble()
        } else if (fg in games && fg != packageName) {
            if (currentGame != fg) { currentGame = fg; applySavedBrightness() }
            showBubble()
        } else {
            currentGame = null
            hideAll()
        }
    }

    // ---------- helpers ----------
    private fun prefs() = getSharedPreferences("gv", MODE_PRIVATE)

    private fun canWrite(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.System.canWrite(this)

    private fun promptWrite() {
        startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(this, "System settings ALLOW koro", Toast.LENGTH_LONG).show()
    }

    private fun setRefresh(rate: Float) {
        if (!canWrite()) { promptWrite(); return }
        saveOrig()
        Settings.System.putFloat(contentResolver, "peak_refresh_rate", rate)
        Settings.System.putFloat(contentResolver, "min_refresh_rate", rate)
    }

    private fun setBrightness(v: Int) {
        if (!canWrite()) { promptWrite(); return }
        saveOrig()
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v.coerceIn(10, 255))
    }

    private setTimeout(ms: Int) {
        if (!canWrite()) { promptWrite(); return }
        saveOrig()
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, ms)
    }

    private fun saveOrig() {
        val p = prefs()
        if (!p.getBoolean("orig_saved", false)) {
            p.edit()
                .putFloat("orig_peak", Settings.System.getFloat(contentResolver, "peak_refresh_rate", 120f))
                .putFloat("orig_min", Settings.System.getFloat(contentResolver, "min_refresh_rate", 60f))
                .putInt("orig_bright", Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 100))
                .putInt("orig_timeout", Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000))
                .putBoolean("orig_saved", true).apply()
        }
    }

    private fun restoreOrig() {
        if (!canWrite()) { promptWrite(); return }
        val p = prefs()
        Settings.System.putFloat(contentResolver, "peak_refresh_rate", p.getFloat("orig_peak", 120f))
        Settings.System.putFloat(contentResolver, "min_refresh_rate", p.getFloat("orig_min", 60f))
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, p.getInt("orig_bright", 100))
        Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, p.getInt("orig_timeout", 60000))
    }

    private fun applySavedBrightness() {
        val g = currentGame ?: return
        val v = prefs().getInt("b_$g", -1)
        if (v > 0 && canWrite()) {
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
        }
    }

    private fun setDnd(on: Boolean): Boolean {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_NONE
                else NotificationManager.INTERRUPTION_FILTER_ALL)
            return true
        }
        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Toast.makeText(this, "DND access ALLOW koro", Toast.LENGTH_LONG).show()
        return false
    }

    // ---------- bubble & panel ----------
    private fun showBubble() {
        if (bubbleView != null) return
        bubbleView = TextView(this).apply {
            text = "G"; setTextColor(Color.BLACK); textSize = 20f; gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; setColor(0xCCFFFFFF.toInt())
            }
            setOnClickListener { togglePanel() }
        }
        val p = baseParams(dp(56), dp(56)).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(120)
        }
        try { wm?.addView(bubbleView, p) } catch (e: Exception) {}
    }

    private fun togglePanel() { if (panelOpen) hidePanel() else showPanel() }

    private fun btn(label: String, click: (Button) -> Unit): Button =
        Button(this).apply {
            text = label; textSize = 11f; isAllCaps = false
            setOnClickListener { click(this) }
        }

    private fun showPanel() {
        hidePanel()
        panelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat(); setColor(0xEE121212.toInt())
            }
        }
        val v = panelView!!

        v.addView(btn(if (dndOn) "Focus: ON" else "Focus: OFF") { b ->
            dndOn = !dndOn
            if (!setDnd(dndOn)) dndOn = !dndOn
            b.text = if (dndOn) "Focus: ON" else "Focus: OFF"
        })
        v.addView(btn("PERF mode") {
            setRefresh(120f); setBrightness(230); setTimeout(1800000); setDnd(true); dndOn = true
            Toast.makeText(this, "Performance mode ON", Toast.LENGTH_SHORT).show()
        })
        v.addView(btn("BATT mode") {
            setRefresh(60f); setBrightness(90); setTimeout(60000)
            Toast.makeText(this, "Battery mode ON", Toast.LENGTH_SHORT).show()
        })
        v.addView(btn("BALANCED") {
            restoreOrig(); setDnd(false); dndOn = false
            Toast.makeText(this, "Settings restore hoye geche", Toast.LENGTH_SHORT).show()
        })
        v.addView(btn("Bright +") {
            val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 100)
            val nv = (cur + 40).coerceAtMost(255)
            setBrightness(nv)
            currentGame?.let { prefs().edit().putInt("b_$it", nv).apply() }
        })
        v.addView(btn("Bright -") {
            val cur = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 100)
            val nv = (cur - 40).coerceAtLeast(20)
            setBrightness(nv)
            currentGame?.let { prefs().edit().putInt("b_$it", nv).apply() }
        })
        v.addView(btn("FPS 120") { setRefresh(120f) })
        v.addView(btn("FPS 60") { setRefresh(60f) })
        v.addView(btn(if (RecordService.recording) "STOP REC" else "REC") {
            if (RecordService.recording) {
                startService(Intent(this, RecordService::class.java).setAction("STOP"))
            } else {
                startActivity(Intent(this, ConsentActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target", "RECORD"))
            }
        })
        v.addView(btn("SHOT") {
            startActivity(Intent(this, ConsentActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("target", "SHOT"))
        })
        v.addView(btn("X Close") { hidePanel() })

        val p = baseParams(WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(190)
        }
        try { wm?.addView(panelView, p); panelOpen = true } catch (e: Exception) {}
    }

    private fun hidePanel() {
        try { if (panelView != null) wm?.removeView(panelView) } catch (e: Exception) {}
        panelView = null; panelOpen = false
    }

    private fun hideAll() {
        hidePanel()
        try { if (bubbleView != null) wm?.removeView(bubbleView) } catch (e: Exception) {}
        bubbleView = null
    }

    private fun baseParams(w: Int, h: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        return WindowManager.LayoutParams(w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        hideAll()
        super.onDestroy()
    }
}
