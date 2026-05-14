package com.gmailnator.auto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var statusTv: TextView
    private lateinit var enableBtn: Button
    private lateinit var serverTv: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTv  = findViewById(R.id.statusTv)
        enableBtn = findViewById(R.id.enableBtn)
        serverTv  = findViewById(R.id.serverTv)

        serverTv.text = "Server: ${Api.SERVER}"

        Api.deviceId   = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Api.deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL

        enableBtn.setOnClickListener { openAccessibility() }

        checkStatus()
    }

    override fun onResume() {
        super.onResume()
        checkStatus()
    }

    private fun checkStatus() {
        val enabled = isAccessibilityEnabled()
        if (enabled) {
            enableBtn.text = "✓ Yoqilgan — Telegram'ni oching"
            enableBtn.isEnabled = false
            statusTv.text = "Faol. Telegram'da email yoki kod maydoni ochilsa — o'zi to'ldiradi."
            checkServer()
        } else {
            enableBtn.text = "Accessibility'ni Yoqish"
            enableBtn.isEnabled = true
            statusTv.text = "⚠ Accessibility ruxsatini bering"
        }
    }

    private fun checkServer() {
        executor.execute {
            val ok = Api.ping()
            runOnUiThread {
                serverTv.text = if (ok) "✓ Server: ${Api.SERVER}" else "✗ Server ulanmadi: ${Api.SERVER}"
            }
        }
    }

    private fun openAccessibility() {
        Toast.makeText(this, "\"Gmailnator Auto\" ni toping va yoqing", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("${packageName}/${packageName}.TelegramAutoService")
    }
}
