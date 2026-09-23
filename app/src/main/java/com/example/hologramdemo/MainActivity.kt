package com.example.hologramdemo

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val extraGames = listOf(
        "com.dts.freefireth", "com.dts.freefiremax", "com.pubg.imobile",
        "com.tencent.ig", "com.activision.callofduty.shooter",
        "com.mobile.legends", "com.supercell.clashofclans"
    )
    private val packages = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val list = findViewById<ListView>(R.id.gameList)
        val edit = findViewById<EditText>(R.id.pkgInput)
        val addBtn = findViewById<Button>(R.id.addBtn)
        val bubbleBtn = findViewById<Button>(R.id.bubbleBtn)

        loadGames()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            packages.map { label(it) })
        list.adapter = adapter

        list.setOnItemClickListener { _, _, pos, _ ->
            val intent = packageManager.getLaunchIntentForPackage(packages[pos])
            if (intent != null) startActivity(intent)
            else Toast.makeText(this, "Game launch kora gelo na", Toast.LENGTH_SHORT).show()
        }

        addBtn.setOnClickListener {
            val pkg = edit.text.toString().trim()
            if (pkg.isEmpty()) return@setOnClickListener
            if (packageManager.getLaunchIntentForPackage(pkg) == null) {
                Toast.makeText(this, "Package paoa jay nai!", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val set = getSharedPreferences("gv", MODE_PRIVATE)
                .getStringSet("manual", mutableSetOf()).orEmpty().toMutableSet()
            set.add(pkg)
            getSharedPreferences("gv", MODE_PRIVATE).edit()
                .putStringSet("manual", set).apply()
            loadGames()
            adapter.clear()
            adapter.addAll(packages.map { label(it) })
            adapter.notifyDataSetChanged()
            edit.setText("")
        }

        bubbleBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                Toast.makeText(this, "Display over other apps ALLOW koro", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startForegroundService(Intent(this, BubbleService::class.java))
            Toast.makeText(this, "Bubble ON - game e gele bubble ashbe", Toast.LENGTH_SHORT).show()
        }
    }

    private fun label(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }

    private fun loadGames() {
        packages.clear()
        val manual = getSharedPreferences("gv", MODE_PRIVATE)
            .getStringSet("manual", emptySet()).orEmpty()
        val installed = try {
            packageManager.getInstalledApplications(0)
                .filter { it.category == ApplicationInfo.CATEGORY_GAME }
                .map { it.packageName }
        } catch (e: Exception) { emptyList() }
        packages.addAll((installed + extraGames + manual).distinct())
    }
}
