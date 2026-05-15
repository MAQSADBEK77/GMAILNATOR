package com.gmailnator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gn", MODE_PRIVATE) }

    private lateinit var tokenScreen: View
    private lateinit var mainScreen: View
    private lateinit var tokenInput: EditText
    private lateinit var tokenError: TextView
    private lateinit var tokenSubmit: Button
    private lateinit var floatBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenScreen  = findViewById(R.id.tokenScreen)
        mainScreen   = findViewById(R.id.mainScreen)
        tokenInput   = findViewById(R.id.tokenInput)
        tokenError   = findViewById(R.id.tokenError)
        tokenSubmit  = findViewById(R.id.tokenSubmit)
        floatBtn     = findViewById(R.id.floatBtn)

        tokenSubmit.setOnClickListener { checkToken() }
        tokenInput.setOnEditorActionListener { _, _, _ -> checkToken(); true }
        floatBtn.setOnClickListener { startFloating() }

        if (prefs.getBoolean("unlocked", false)) showMain() else showToken()
    }

    private fun checkToken() {
        val input = tokenInput.text.toString().trim()
        if (input.isEmpty()) return
        if (sha256(input) == TOKEN_HASH) {
            prefs.edit().putBoolean("unlocked", true).apply()
            tokenInput.text.clear()
            showMain()
        } else {
            tokenError.text = "Noto'g'ri kalit"
            tokenError.visibility = View.VISIBLE
            tokenInput.text.clear()
        }
    }

    private fun showToken() {
        tokenScreen.visibility = View.VISIBLE
        mainScreen.visibility  = View.GONE
    }

    private fun showMain() {
        tokenScreen.visibility = View.GONE
        mainScreen.visibility  = View.VISIBLE
        floatBtn.isEnabled = true
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
    }

    private fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    companion object {
        // SHA-256("MAQSADBEK777")
        private const val TOKEN_HASH = "fd1acf8bc4d52ab76b793a66ea659a30e4299e0923dabe8021bb7a3424ef33ce"
    }
}
