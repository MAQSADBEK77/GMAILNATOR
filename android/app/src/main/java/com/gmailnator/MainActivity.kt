package com.gmailnator

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences("gn", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val keyInput = findViewById<EditText>(R.id.keyInput)
        val saveBtn  = findViewById<Button>(R.id.saveBtn)
        val floatBtn = findViewById<Button>(R.id.floatBtn)
        val statusTv = findViewById<TextView>(R.id.statusTv)

        keyInput.setText(prefs.getString("api_key", ""))

        saveBtn.setOnClickListener {
            val k = keyInput.text.toString().trim()
            if (k.isEmpty()) { toast("Kalit kiriting!"); return@setOnClickListener }
            prefs.edit().putString("api_key", k).apply()
            toast("Kalit saqlandi ✓")
        }

        floatBtn.setOnClickListener {
            val key = prefs.getString("api_key", "")
            if (key.isNullOrEmpty()) { toast("Avval API kalitni saqlang!"); return@setOnClickListener }
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                statusTv.text = "Ruxsat bering, keyin tugmani qayta bosing"
            } else {
                startService(Intent(this, FloatingService::class.java))
                statusTv.text = "✓ Floating panel yoqildi — ilovani yoping"
                floatBtn.text = "✓ Ishlayapti (ilovani yoping)"
            }
        }

        if (Settings.canDrawOverlays(this) && !prefs.getString("api_key","").isNullOrEmpty()) {
            statusTv.text = "Tayyor. Floating panel yoqing."
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
