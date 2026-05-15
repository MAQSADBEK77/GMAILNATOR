package com.gmailnator.auto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gna", MODE_PRIVATE) }

    private lateinit var tokenScreen: View
    private lateinit var mainScreen: View
    private lateinit var tokenInput: EditText
    private lateinit var tokenError: TextView
    private lateinit var statusTv: TextView
    private lateinit var enableBtn: Button
    private lateinit var controlRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tokenScreen = findViewById(R.id.tokenScreen)
        mainScreen  = findViewById(R.id.mainScreen)
        tokenInput  = findViewById(R.id.tokenInput)
        tokenError  = findViewById(R.id.tokenError)
        statusTv    = findViewById(R.id.statusTv)
        enableBtn   = findViewById(R.id.enableBtn)
        controlRow  = findViewById(R.id.controlRow)

        val stopBtn    = findViewById<Button>(R.id.stopBtn)
        val restartBtn = findViewById<Button>(R.id.restartBtn)

        findViewById<Button>(R.id.tokenSubmit).setOnClickListener { checkToken() }
        tokenInput.setOnEditorActionListener { _, _, _ -> checkToken(); true }
        enableBtn.setOnClickListener  { openAccessibility() }
        stopBtn.setOnClickListener    { sendAction(TelegramAutoService.ACTION_STOP) }
        restartBtn.setOnClickListener { sendAction(TelegramAutoService.ACTION_RESTART) }

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
            tokenError.text = "✗ Noto'g'ri kalit"
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
        updateAccessibilityState()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.getBoolean("unlocked", false)) updateAccessibilityState()
    }

    private fun updateAccessibilityState() {
        val enabled = isAccessibilityEnabled()
        if (enabled) {
            enableBtn.text = "✓ Yoqilgan — Telegram'ni oching"
            enableBtn.isEnabled = false
            controlRow.visibility = View.VISIBLE
            statusTv.text = "Faol. Email yoki kod maydoni ochilsa — o'zi to'ldiradi."
        } else {
            enableBtn.text = "Accessibility'ni Yoqish"
            enableBtn.isEnabled = true
            controlRow.visibility = View.GONE
            statusTv.text = "⚠ Accessibility ruxsatini bering"
        }
    }

    private fun sendAction(action: String) {
        startService(Intent(this, TelegramAutoService::class.java).apply { this.action = action })
        Toast.makeText(this,
            if (action == TelegramAutoService.ACTION_STOP) "⏹ To'xtatildi" else "▶ Qayta boshlandi",
            Toast.LENGTH_SHORT).show()
    }

    private fun openAccessibility() {
        Toast.makeText(this, "\"Gmailnator Auto\" ni toping va yoqing", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun isAccessibilityEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return s.contains("${packageName}/${packageName}.TelegramAutoService")
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
