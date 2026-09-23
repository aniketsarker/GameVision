package com.example.hologramdemo

import android.app.Activity
import android.app.MediaProjectionManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

class ConsentActivity : Activity() {

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (res == RESULT_OK && d != null) {
            RecordService.resultCode = res
            RecordService.projectionData = d
            startForegroundService(
                Intent(this, RecordService::class.java)
                    .setAction(intent?.getStringExtra("target") ?: "SHOT"))
        } else {
            Toast.makeText(this, "Permission deny kora hoyeche", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}
