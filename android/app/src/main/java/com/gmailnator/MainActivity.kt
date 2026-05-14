package com.gmailnator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var statusTv: TextView
    private lateinit var floatBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTv = findViewById(R.id.statusTv)
        floatBtn = findViewById(R.id.floatBtn)

        Api.deviceId   = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Api.deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL

        floatBtn.setOnClickListener { startFloating() }

        checkServer()
    }

    private fun checkServer() {
        statusTv.text = "Server tekshirilmoqda..."
        floatBtn.isEnabled = false
        executor.execute {
            try {
                Api.connect()
                runOnUiThread {
                    statusTv.text = "✓ Ulandi — ${Api.SERVER}"
                    floatBtn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusTv.text = "✗ ${e.message}"
                    floatBtn.isEnabled = false
                }
            }
        }
    }

    private fun startFloating() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
            floatBtn.text = "Ruxsat bering, keyin bosing"
            return
        }
        startService(Intent(this, FloatingService::class.java))
        floatBtn.text = "✓ Ishlamoqda — ilovani yoping"
        statusTv.text = "Floating panel yoqildi"
    }

    override fun onResume() {
        super.onResume()
        checkServer()
    }
}
