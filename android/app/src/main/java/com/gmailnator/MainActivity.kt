package com.gmailnator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gn", Context.MODE_PRIVATE) }
    private val executor = Executors.newSingleThreadExecutor()

    private val deviceId by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }
    private val deviceName by lazy {
        android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
    }

    // Views
    private lateinit var setupView: View
    private lateinit var loginView: View
    private lateinit var mainView: View
    private lateinit var serverInput: EditText
    private lateinit var loginInput: EditText
    private lateinit var passInput: EditText
    private lateinit var statusTv: TextView
    private lateinit var floatBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupView  = findViewById(R.id.setupView)
        loginView  = findViewById(R.id.loginView)
        mainView   = findViewById(R.id.mainView)
        serverInput = findViewById(R.id.serverInput)
        loginInput  = findViewById(R.id.loginInput)
        passInput   = findViewById(R.id.passInput)
        statusTv    = findViewById(R.id.statusTv)
        floatBtn    = findViewById(R.id.floatBtn)

        val savedUrl = prefs.getString("server_url", "") ?: ""
        serverInput.setText(savedUrl)

        findViewById<Button>(R.id.saveServerBtn).setOnClickListener { saveServer() }
        findViewById<Button>(R.id.loginBtn).setOnClickListener { doLogin() }
        floatBtn.setOnClickListener { startFloating() }
        findViewById<Button>(R.id.logoutBtn).setOnClickListener { logout() }

        determineScreen()
    }

    private fun determineScreen() {
        val url = prefs.getString("server_url", "") ?: ""
        if (url.isEmpty()) { showOnly(setupView); return }

        Api.serverUrl = url
        val tok  = prefs.getString("token", "") ?: ""
        val time = prefs.getLong("token_time", 0L)
        val valid = tok.isNotEmpty() && (System.currentTimeMillis() - time) < 23 * 3600 * 1000L

        if (!valid) { showOnly(loginView); return }

        Api.token = tok
        showOnly(mainView)
        statusTv.text = "Server: $url"
    }

    private fun saveServer() {
        val url = serverInput.text.toString().trim().trimEnd('/')
        if (url.isEmpty()) { toast("Server IP kiriting!"); return }
        prefs.edit().putString("server_url", url).apply()
        Api.serverUrl = url
        showOnly(loginView)
    }

    private fun doLogin() {
        val login = loginInput.text.toString().trim()
        val pass  = passInput.text.toString().trim()
        if (login.isEmpty() || pass.isEmpty()) { toast("Login va parolni kiriting!"); return }

        val btn = findViewById<Button>(R.id.loginBtn)
        btn.isEnabled = false
        btn.text = "Tekshirilmoqda..."
        statusTv.text = ""

        executor.execute {
            try {
                val tok = Api.login(login, pass, deviceId, deviceName)
                prefs.edit()
                    .putString("token", tok)
                    .putLong("token_time", System.currentTimeMillis())
                    .apply()
                Api.token = tok
                runOnUiThread {
                    showOnly(mainView)
                    statusTv.text = "✓ Kirish muvaffaqiyatli"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    statusTv.text = "✗ ${e.message}"
                    btn.isEnabled = true
                    btn.text = "Kirish"
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

    private fun logout() {
        stopService(Intent(this, FloatingService::class.java))
        prefs.edit().remove("token").remove("token_time").apply()
        Api.token = ""
        loginInput.text.clear()
        passInput.text.clear()
        showOnly(loginView)
    }

    private fun showOnly(v: View) {
        setupView.visibility = View.GONE
        loginView.visibility = View.GONE
        mainView.visibility  = View.GONE
        v.visibility = View.VISIBLE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
