package com.gmailnator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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

        if (isSessionValid()) showMain() else showToken()
    }

    private fun isSessionValid(): Boolean {
        val enc = prefs.getString("s", null) ?: return false
        val ts  = decrypt(enc)?.toLongOrNull() ?: return false
        return System.currentTimeMillis() - ts < SESSION_MS
    }

    private fun checkToken() {
        val input = tokenInput.text.toString().trim()
        if (input.isEmpty()) return
        if (sha256(input) == TOKEN_HASH) {
            prefs.edit().putString("s", encrypt(System.currentTimeMillis().toString())).apply()
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

    // ── AES-256-GCM ──────────────────────────────────
    private val aesKey by lazy {
        SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest("MAQSADBEK777".toByteArray()),
            "AES"
        )
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, aesKey)
        val iv = c.iv
        val ct = c.doFinal(plain.toByteArray())
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String? = try {
        val b = Base64.decode(encoded, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, b, 0, 12))
        String(c.doFinal(b, 12, b.size - 12))
    } catch (e: Exception) { null }

    private fun sha256(s: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_HASH = "fd1acf8bc4d52ab76b793a66ea659a30e4299e0923dabe8021bb7a3424ef33ce"
        private const val SESSION_MS = 96L * 60 * 60 * 1000
    }
}
