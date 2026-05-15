package com.gmailnator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gn", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tokenScreen: View
    private lateinit var mainScreen: View
    private lateinit var tokenInput: EditText
    private lateinit var tokenError: TextView
    private lateinit var tokenSubmit: Button
    private lateinit var statusTv: TextView
    private lateinit var floatBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenScreen  = findViewById(R.id.tokenScreen)
        mainScreen   = findViewById(R.id.mainScreen)
        tokenInput   = findViewById(R.id.tokenInput)
        tokenError   = findViewById(R.id.tokenError)
        tokenSubmit  = findViewById(R.id.tokenSubmit)
        statusTv     = findViewById(R.id.statusTv)
        floatBtn     = findViewById(R.id.floatBtn)

        Api.deviceId   = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Api.deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL

        tokenSubmit.setOnClickListener { checkToken() }
        tokenInput.setOnEditorActionListener { _, _, _ -> checkToken(); true }
        floatBtn.setOnClickListener { startFloating() }

        if (prefs.getBoolean("unlocked", false)) showMain() else showToken()
    }

    private fun checkToken() {
        val input = tokenInput.text.toString().trim()
        if (input.isEmpty()) return
        tokenSubmit.isEnabled = false
        tokenSubmit.text = "Tekshirilmoqda..."
        tokenError.visibility = View.GONE

        executor.execute {
            try {
                val ok = Api.verifyToken(input)
                runOnUiThread {
                    if (ok) {
                        prefs.edit().putBoolean("unlocked", true).apply()
                        tokenInput.text.clear()
                        showMain()
                    } else {
                        tokenError.text = "Noto'g'ri kalit"
                        tokenError.visibility = View.VISIBLE
                        tokenInput.text.clear()
                    }
                    tokenSubmit.isEnabled = true
                    tokenSubmit.text = "Kirish"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tokenError.text = "Server ulanmadi — server.bat yoqing"
                    tokenError.visibility = View.VISIBLE
                    tokenSubmit.isEnabled = true
                    tokenSubmit.text = "Kirish"
                }
            }
        }
    }

    private fun showToken() {
        tokenScreen.visibility = View.VISIBLE
        mainScreen.visibility  = View.GONE
    }

    private fun showMain() {
        tokenScreen.visibility = View.GONE
        mainScreen.visibility  = View.VISIBLE
        checkServer()
    }

    private fun checkServer() {
        statusTv.text = "Server tekshirilmoqda..."
        floatBtn.isEnabled = false
        executor.execute {
            try {
                Api.connect()
                runOnUiThread {
                    statusTv.text = "Server ulandi"
                    floatBtn.isEnabled = true
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusTv.text = "Server ulanmadi: ${e.message}"
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
        floatBtn.text = "Ishlamoqda — ilovani yoping"
        statusTv.text = "Floating panel yoqildi"
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("unlocked", false)) checkServer()
    }
}
