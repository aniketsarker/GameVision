package com.example.hologramdemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.app.MediaProjectionManager
import android.view.WindowManager
import android.widget.Toast

class RecordService : Service() {

    companion object {
        var resultCode = 0
        var projectionData: Intent? = null
        var recording = false
    }

    private var recorder: MediaRecorder? = null
    private var vd: VirtualDisplay? = null
    private var projection: android.media.projection.MediaProjection? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        startFg()
        when (intent?.action) {
            "RECORD" -> startRecord()
            "STOP" -> stopRecord()
            "SHOT" -> shot()
        }
        return START_NOT_STICKY
    }

    private fun startFg() {
        val ch = NotificationChannel("rec", "Recording", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "rec")
            .setContentTitle("GameVision capture service")
            .setSmallIcon(android.R.drawable.ic_menu_camera).build()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(2, n)
    }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        val b = wm.currentWindowMetrics.bounds
        return Pair(b.width(), b.height())
    }

    @Suppress("DEPRECATION")
    private fun startRecord() {
        if (recording) return
        val data = projectionData ?: return
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projection = mpm.getMediaProjection(resultCode, data)
            val (w, h) = screenSize()
            val dpi = resources.displayMetrics.densityDpi

            recorder = MediaRecorder(this).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(12_000_000)
                setVideoFrameRate(60)
                setVideoSize(w, h)
            }
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "gv_${System.currentTimeMillis()}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_DIRECTORY, "Movies/GameVision")
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
            val pfd = contentResolver.openFileDescriptor(uri, "rw")!!
            recorder!!.setOutputFile(pfd.fileDescriptor)
            recorder!!.prepare()

            vd = projection!!.createVirtualDisplay("gvrec", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface, null, null)
            recorder!!.start()
            recording = true
            Toast.makeText(this, "Recording START", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Record start hoy nai: ${e.message}", Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    private fun stopRecord() {
        try {
            recorder?.stop()
        } catch (e: Exception) { }
        recorder?.reset(); recorder?.release(); recorder = null
        vd?.release(); vd = null
        projection?.stop(); projection = null
        recording = false
        Toast.makeText(this, "Recording saved (Movies/GameVision)", Toast.LENGTH_LONG).show()
        stopSelf()
    }

    private fun shot() {
        val data = projectionData ?: run { stopSelf(); return }
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projection = mpm.getMediaProjection(resultCode, data)
            val (w, h) = screenSize()
            val dpi = resources.displayMetrics.densityDpi
            val reader = ImageReader.newInstance(w, h, android.graphics.PixelFormat.RGBA_8888, 2)
            val svd = projection!!.createVirtualDisplay("gvshot", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, null)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        val plane = image.planes[0]
                        val buf = plane.buffer
                        val rowStride = plane.rowStride
                        val pixelStride = plane.pixelStride
                        val pad = rowStride - pixelStride * w
                        val bmp = Bitmap.createBitmap(w + pad / pixelStride, h, Bitmap.Config.ARGB_8888)
                        bmp.copyPixelsFromBuffer(buf)
                        val crop = Bitmap.createBitmap(bmp, 0, 0, w, h)
                        image.close()
                        val values = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, "gv_${System.currentTimeMillis()}.png")
                            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_DIRECTORY, "Pictures/GameVision")
                        }
                        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        uri?.let { contentResolver.openOutputStream(it)?.use { os ->
                            crop.compress(Bitmap.CompressFormat.PNG, 100, os)
                        } }
                        Toast.makeText(this, "Screenshot saved!", Toast.LENGTH_SHORT).show()
                    } else Toast.makeText(this, "Shot fail", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { e.printStackTrace() }
                svd.release(); reader.close()
                projection?.stop(); projection = null
                stopSelf()
            }, 700)
        } catch (e: Exception) {
            e.printStackTrace(); stopSelf()
        }
    }

    override fun onDestroy() {
        if (recording) stopRecord()
        super.onDestroy()
    }
}
