package com.gmailnator

import android.app.*
import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import kotlin.math.abs

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var isPanelOpen = false

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private val prefs by lazy { getSharedPreferences("gn", Context.MODE_PRIVATE) }
    private val apiKey get() = prefs.getString("api_key", "") ?: ""

    private var curEmail = ""
    private var lastCode = ""
    private val seenIds = mutableSetOf<String>()
    private var autoRunnable: Runnable? = null

    // Panel views (set after inflate)
    private var emailTv: TextView? = null
    private var codeTv: TextView? = null
    private var statusTv: TextView? = null

    override fun onBind(i: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotif()
        showBubble()
    }

    // ── Foreground notification ─────────────────────────
    private fun startForegroundNotif() {
        val ch = NotificationChannel("gn", "Gmailnator", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val n = NotificationCompat.Builder(this, "gn")
            .setContentTitle("Gmailnator")
            .setContentText("Floating panel faol")
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .build()
        startForeground(1, n)
    }

    // ── Bubble ──────────────────────────────────────────
    private fun showBubble() {
        val params = overlayParams(80.dp, 80.dp).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 24.dp; y = 300.dp
        }

        val bubble = TextView(this).apply {
            text = "📧"
            textSize = 28f
            gravity = Gravity.CENTER
            background = roundRect(Color.parseColor("#3b5bdb"), 40.dp)
        }

        makeDraggable(bubble, params)
        bubble.setOnClickListener { if (!isPanelOpen) openPanel() }

        wm.addView(bubble, params)
        bubbleView = bubble
    }

    // ── Panel ───────────────────────────────────────────
    private fun openPanel() {
        isPanelOpen = true
        val params = overlayParams(300.dp, WindowManager.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 80.dp
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
            background = roundRect(Color.parseColor("#15182a"), 20.dp)
        }

        // Email row
        emailTv = TextView(this).apply {
            text = if (curEmail.isEmpty()) "— hali yaratilmagan —" else curEmail
            setTextColor(Color.parseColor("#748ffc"))
            textSize = 12f
            setPadding(0, 0, 0, 4.dp)
        }

        // Code row
        codeTv = TextView(this).apply {
            text = if (lastCode.isEmpty()) "—" else lastCode
            setTextColor(Color.parseColor("#51cf66"))
            textSize = 32f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        // Status
        statusTv = TextView(this).apply {
            text = "Tayyor"
            setTextColor(Color.parseColor("#5c6380"))
            textSize = 11f
            gravity = Gravity.CENTER
        }

        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#222442"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1.dp).apply {
                topMargin = 10.dp; bottomMargin = 10.dp
            }
        }

        panel.addView(emailTv)
        panel.addView(codeTv)
        panel.addView(statusTv)
        panel.addView(divider)
        panel.addView(makeBtn("📧  GMAIL OL", "#3b5bdb", "#7048e8") { doGenEmail() })
        panel.addView(space(8.dp))
        panel.addView(makeBtn("🔍  KOD OL", "#2f9e44", "#37b24d") { doGetCode() })
        panel.addView(space(8.dp))
        panel.addView(makeBtn("🔄  YANGI GMAIL", "#252840", "#252840") { doNewEmail() })
        panel.addView(space(12.dp))
        panel.addView(makeBtn("✕  Yopish", "#1a1d27", "#1a1d27") { closePanel() })

        wm.addView(panel, params)
        panelView = panel
    }

    private fun closePanel() {
        panelView?.let { wm.removeView(it) }
        panelView = null
        isPanelOpen = false
    }

    // ── Actions ─────────────────────────────────────────
    private fun doGenEmail() {
        setStatus("Yaratilmoqda...")
        seenIds.clear(); lastCode = ""
        stopAutoRefresh()
        executor.execute {
            try {
                val email = Api.generateEmail(apiKey)
                curEmail = email
                copyToClipboard(email)
                handler.post {
                    emailTv?.text = email
                    codeTv?.text = "—"
                    setStatus("✓ Nusxalandi!")
                    startAutoRefresh()
                }
            } catch (e: Exception) {
                handler.post { setStatus("✗ ${e.message}") }
            }
        }
    }

    private fun doGetCode(isAuto: Boolean = false) {
        if (!isAuto) setStatus("Tekshirilmoqda...")
        executor.execute {
            try {
                val msgs = Api.getInbox(apiKey, curEmail)
                if (msgs.isEmpty()) {
                    handler.post { if (!isAuto) setStatus("📭 Xabar kelmadi") }
                    return@execute
                }
                val unseen = msgs.filter { it.id !in seenIds }
                val target = (if (unseen.isNotEmpty()) unseen else msgs).first()
                seenIds.add(target.id)
                val content = Api.getMessage(apiKey, target.id)
                val code = Api.extractCode(content)
                handler.post {
                    if (code != null) {
                        lastCode = code
                        codeTv?.text = code
                        copyToClipboard(code)
                        setStatus("✓ Kod nusxalandi!")
                        stopAutoRefresh()
                        beep()
                    } else {
                        setStatus("Xabar keldi, kod topilmadi")
                    }
                }
            } catch (e: Exception) {
                handler.post { if (!isAuto) setStatus("✗ ${e.message}") }
            }
        }
    }

    private fun doNewEmail() {
        stopAutoRefresh()
        codeTv?.text = "—"; emailTv?.text = "Yaratilmoqda..."
        doGenEmail()
    }

    // ── Auto refresh ─────────────────────────────────────
    private fun startAutoRefresh() {
        stopAutoRefresh()
        autoRunnable = object : Runnable {
            override fun run() {
                if (curEmail.isNotEmpty()) doGetCode(true)
                handler.postDelayed(this, 15_000)
            }
        }
        handler.postDelayed(autoRunnable!!, 15_000)
    }

    private fun stopAutoRefresh() {
        autoRunnable?.let { handler.removeCallbacks(it) }
        autoRunnable = null
    }

    // ── Helpers ──────────────────────────────────────────
    private fun setStatus(msg: String) { statusTv?.text = msg }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", text))
    }

    private fun beep() {
        try {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 300)
        } catch (e: Exception) {}
    }

    private fun makeBtn(label: String, c1: String, c2: String, click: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            background = roundGradient(Color.parseColor(c1), Color.parseColor(c2), 14.dp)
            setPadding(0, 14.dp, 0, 14.dp)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isAllCaps = false
            setOnClickListener { click() }
        }
    }

    private fun space(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, h)
    }

    private fun roundRect(color: Int, r: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = r.toFloat()
    }

    private fun roundGradient(c1: Int, c2: Int, r: Int) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = r.toFloat()
        }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    private fun overlayParams(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    )

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var startX = 0f; var startY = 0f; var startPx = 0; var startPy = 0; var moved = false
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; startPx = params.x; startPy = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startX).toInt(); val dy = (event.rawY - startY).toInt()
                    if (abs(dx) > 5 || abs(dy) > 5) { moved = true }
                    params.x = startPx + dx; params.y = startPy - dy
                    if (panelView == null) wm.updateViewLayout(view, params); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) view.performClick(); true }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        bubbleView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        panelView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
    }
}
