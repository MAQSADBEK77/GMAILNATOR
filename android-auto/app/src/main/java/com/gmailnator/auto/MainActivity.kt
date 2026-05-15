package com.gmailnator.auto

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gna", MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var tokenScreen: View
    private lateinit var mainScreen: View
    private lateinit var tokenInput: EditText
    private lateinit var tokenError: TextView
    private lateinit var statusTv: TextView
    private lateinit var enableBtn: Button
    private lateinit var serverTv: TextView
    private lateinit var stopBtn: Button
    private lateinit var restartBtn: Button
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
        serverTv    = findViewById(R.id.serverTv)
        stopBtn     = findViewById(R.id.stopBtn)
        restartBtn  = findViewById(R.id.restartBtn)
        controlRow  = findViewById(R.id.controlRow)

        Api.deviceId   = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        Api.deviceName = android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL

        serverTv.text = "Server: ${Api.SERVER}"

        findViewById<Button>(R.id.tokenSubmit).setOnClickListener { checkToken() }
        tokenInput.setOnEditorActionListener { _, _, _ -> checkToken(); true }
        enableBtn.setOnClickListener  { openAccessibility() }
        stopBtn.setOnClickListener    { sendAction(TelegramAutoService.ACTION_STOP) }
        restartBtn.setOnClickListener { sendAction(TelegramAutoService.ACTION_RESTART) }

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
            checkServer()
        } else {
            enableBtn.text = "Accessibility'ni Yoqish"
            enableBtn.isEnabled = true
            controlRow.visibility = View.GONE
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
}
