package com.gmailnator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gn", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tokenScreen: View
    private lateinit var mainScreen: View
    private lateinit var tokenInput: EditText
    private lateinit var tokenError: TextView
    private lateinit var statusTv: TextView
    private lateinit var floatBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenScreen = findViewById(R.id.tokenScreen)
        mainScreen  = findViewById(R.id.mainScreen)
        tokenInput  = findViewById(R.id.tokenInput)
        tokenError  = findViewById(R.id.tokenError)
        statusTv    = findViewById(R.id.statusTv)
        floatBtn    = findViewById(R.id.floatBtn)

        Api.deviceId   = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Api.deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL

        findViewById<Button>(R.id.tokenSubmit).setOnClickListener { checkToken() }
        floatBtn.setOnClickListener { startFloating() }
        tokenInput.setOnEditorActionListener { _, _, _ -> checkToken(); true }

        if (prefs.getBoolean("unlocked", false)) showMain() else showToken()
    }

    // ── Token tekshirish ─────────────────────────────────
    private fun checkToken() {
        val input = tokenInput.text.toString().trim()
        if (isValidToken(input)) {
            prefs.edit().putBoolean("unlocked", true).apply()
            tokenInput.text.clear()
            showMain()
        } else {
            tokenError.text = "✗ Noto'g'ri kalit"
            tokenError.visibility = View.VISIBLE
            tokenInput.text.clear()
        }
    }

    private fun isValidToken(input: String): Boolean {
        val h = sha256(input)
        // SHA-256("MAQSADBEK777") — ProGuard bu qiymatni obfuskat qiladi
        val c = charArrayOf(
            '3','b','2','0','e','3','1','3',
            '2','7','1','5','5','a','6','f',
            '5','b','2','e','f','8','2','3',
            'a','4','a','0','a','7','2','f',
            'e','1','3','8','0','4','8','d',
            '5','1','d','c','5','6','8','1',
            '5','a','5','e','1','8','5','b',
            'b','2','b','2','5','5','6','0'
        )
        return h == String(c)
    }

    private fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    // ── UI ───────────────────────────────────────────────
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
        if (prefs.getBoolean("unlocked", false)) checkServer()
    }
}
