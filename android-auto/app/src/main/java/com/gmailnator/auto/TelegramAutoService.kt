package com.gmailnator.auto

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.graphics.Rect
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

class TelegramAutoService : AccessibilityService() {

    enum class State { IDLE, EMAIL_PASTED, POLLING }

    private var state          = State.IDLE
    private var curEmail       = ""
    private var preloadedEmail = ""       // telefon ekranida avvaldan tayyor email
    private var isPreloading   = false
    private val seenIds        = mutableSetOf<String>()
    private val triedCodes     = mutableSetOf<String>()  // yomon/sinab ko'rilgan kodlar
    private val executor       = Executors.newSingleThreadExecutor()
    private val handler        = Handler(Looper.getMainLooper())
    private var pollJob: Runnable? = null
    private var lastEmailFieldHash = 0

    private val TELEGRAM_PKGS = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram"
    )

    private val PHONE_KW = listOf(
        "phone number", "your phone", "enter your phone", "phone",
        "raqam", "telefon", "raqamingiz",
        "номер телефона", "номер", "введите номер", "ваш номер"
    )

    private val EMAIL_KW = listOf(
        "email", "e-mail", "login email", "choose a login", "your email",
        "add email", "valid email", "protect your account",
        "enter your email address", "enter email",
        "почт", "электронной", "укажите",
        "введите email", "выберите email", "ваш email",
        "emailingiz", "emailini", "pochta", "elektron", "email kiriting"
    )

    private val CODE_KW = listOf(
        "verification code", "confirmation code",
        "code", "verification", "login code",
        "enter code", "otp", "check your email",
        "we've sent", "sent the code",
        "код", "введите код", "подтвер", "проверочн",
        "kod", "tasdiqlash", "kodni"
    )

    private val ERROR_KW = listOf(
        "not allowed", "not_allowed", "email_not_allowed", "email not allowed",
        "invalid email", "#400",
        "не разрешен", "не допускается", "недействительн",
        "ruxsat yo'q", "noto'g'ri email"
    )

    private val NEXT_TEXTS = listOf(
        "Next", "Continue", "Done", "OK", "Send", "Submit", "Verify", "Confirm",
        "Далее", "Продолжить", "Готово", "ОК", "Отправить", "Подтвердить",
        "Keyingi", "Davom", "Tasdiqlash", "Yuborish", "Bajarish"
    )

    companion object {
        const val ACTION_STOP    = "com.gmailnator.auto.STOP"
        const val ACTION_RESTART = "com.gmailnator.auto.RESTART"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        showToast("Gmailnator Auto: faol ✓")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPolling(); fullReset()
                showToast("⏹ To'xtatildi")
            }
            ACTION_RESTART -> {
                stopPolling(); fullReset()
                showToast("▶ Qayta boshlandi")
                handler.postDelayed({ checkScreen() }, 600)
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in TELEGRAM_PKGS) return
        handler.removeCallbacksAndMessages("chk")
        handler.postAtTime({ checkScreen() }, "chk", SystemClock.uptimeMillis() + 400)
    }

    private fun checkScreen() {
        val root  = rootInActiveWindow ?: return
        val nodes = allNodes(root)
        val texts = nodes.flatMap {
            listOfNotNull(it.text?.toString(), it.hintText?.toString(), it.contentDescription?.toString())
        }.map { it.lowercase() }

        // ── Xato: EMAIL_NOT_ALLOWED ───────────────────────
        if (ERROR_KW.any { kw -> texts.any { it.contains(kw) } }) {
            showToast("Email ruxsat yo'q — yangi tayorlanmoqda...")
            stopPolling()
            state = State.IDLE; curEmail = ""; lastEmailFieldHash = 0
            seenIds.clear(); triedCodes.clear()
            preloadedEmail = ""
            preloadEmail()                          // darhol yangi email tayorlaymiz
            performGlobalAction(GLOBAL_ACTION_BACK)
            handler.postDelayed({ checkScreen() }, 1200)
            return
        }

        val isPhone = PHONE_KW.any { kw -> texts.any { it.contains(kw) } }
        val isEmail = EMAIL_KW.any { kw -> texts.any { it.contains(kw) } }
        val isCode  = CODE_KW.any  { kw -> texts.any { it.contains(kw) } }

        // ── Orqaga bosildi — aktiv holdan chiqdik ─────────
        if (!isEmail && !isCode && state != State.IDLE) {
            stopPolling(); resetState()
        }

        // ── Kod → Email (orqaga qaytildi) ─────────────────
        if (isEmail && state == State.POLLING) {
            stopPolling()
            state = State.IDLE; curEmail = ""; lastEmailFieldHash = 0
            seenIds.clear(); triedCodes.clear()
        }

        // ── Telefon ekrani — emailni oldindan tayorla ─────
        if (isPhone && !isEmail && state == State.IDLE
            && preloadedEmail.isEmpty() && !isPreloading) {
            preloadEmail()
        }

        // ── Email ekrani ──────────────────────────────────
        if (isEmail && state == State.IDLE) {
            // Eski email bor bo'lsa — o'chir
            val filled = nodes.firstOrNull {
                (it.isEditable || it.isFocused) && it.isEnabled && !it.text.isNullOrEmpty()
            }
            if (filled != null) {
                setText(filled, ""); lastEmailFieldHash = 0
                handler.postDelayed({ checkScreen() }, 600)
                return
            }
            val field = findEmailField(nodes)
            if (field != null) {
                val h = System.identityHashCode(field)
                if (h != lastEmailFieldHash) { lastEmailFieldHash = h; handleEmail(field) }
            }
            return
        }

        // ── Kod ekrani ────────────────────────────────────
        if (isCode && state == State.EMAIL_PASTED) {
            state = State.POLLING; startPolling()
        }

        if (!isEmail && !isCode && state == State.IDLE) lastEmailFieldHash = 0
    }

    // ── Email oldindan tayorlash ───────────────────────────
    private fun preloadEmail() {
        if (isPreloading) return
        isPreloading = true
        showToast("📧 Email tayorlanmoqda...")
        executor.execute {
            try {
                val email = Api.generateEmail()
                handler.post { preloadedEmail = email; isPreloading = false; showToast("📧 Tayyor: $email") }
            } catch (e: Exception) {
                handler.post { isPreloading = false }
            }
        }
    }

    private fun findEmailField(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        nodes.firstOrNull { it.isFocused && it.isEnabled && it.text.isNullOrEmpty() }?.let { return it }
        nodes.firstOrNull { it.isEditable && it.isEnabled && it.text.isNullOrEmpty() }?.let { return it }
        nodes.firstOrNull { it.isFocused && it.isEnabled }?.let { return it }
        nodes.firstOrNull { it.isEditable && it.isEnabled }?.let { return it }
        return null
    }

    private fun handleEmail(field: AccessibilityNodeInfo) {
        state = State.EMAIL_PASTED
        seenIds.clear(); triedCodes.clear()

        val ready = preloadedEmail
        preloadedEmail = ""

        if (ready.isNotEmpty()) {
            // Tayyor email bor — darhol yozamiz
            curEmail = ready
            val freshField = rootInActiveWindow?.let { allNodes(it) }?.let { ns ->
                ns.firstOrNull { it.isEditable && it.isEnabled && it.text.isNullOrEmpty() }
                    ?: ns.firstOrNull { it.isEditable && it.isEnabled }
            } ?: field
            setText(freshField, ready)
            clipboard(ready)
            showToast("✓ $ready")
            handler.postDelayed({ rootInActiveWindow?.let { clickNext(it) } }, 600)
        } else {
            // Yangi generatsiya
            showToast("Email yaratilmoqda...")
            executor.execute {
                try {
                    val email = Api.generateEmail()
                    curEmail = email
                    handler.post {
                        val freshField = rootInActiveWindow?.let { allNodes(it) }?.let { ns ->
                            ns.firstOrNull { it.isEditable && it.isEnabled && it.text.isNullOrEmpty() }
                                ?: ns.firstOrNull { it.isEditable && it.isEnabled }
                        }
                        if (freshField != null) setText(freshField, email)
                        clipboard(email); showToast("✓ $email")
                        handler.postDelayed({ rootInActiveWindow?.let { clickNext(it) } }, 800)
                    }
                } catch (e: Exception) {
                    handler.post { state = State.IDLE; lastEmailFieldHash = 0; showToast("✗ ${e.message}") }
                }
            }
        }
    }

    // ── Kod polling ───────────────────────────────────────
    private fun startPolling() {
        stopPolling()
        showToast("Kod kutilmoqda...")
        var attempts = 0
        pollJob = object : Runnable {
            override fun run() {
                if (++attempts > 180) { handler.post { resetState(); showToast("Timeout") }; return }
                executor.execute {
                    try {
                        val msgs = Api.getInbox(curEmail)
                        val newMsgs = msgs.filter { it.id !in seenIds }
                        if (newMsgs.isEmpty()) { handler.postDelayed(this, 2000); return@execute }

                        for (msg in newMsgs) {
                            seenIds.add(msg.id)
                            val code = Api.extractCode(Api.getMessage(msg.id))
                            if (code != null && isValidCode(code) && code !in triedCodes) {
                                handler.post { pasteCode(code) }
                                return@execute
                            }
                        }
                        handler.postDelayed(this, 2000)
                    } catch (e: Exception) {
                        handler.postDelayed(this, 2000)
                    }
                }
            }
        }
        handler.postDelayed(pollJob!!, 3000)
    }

    // ── Kod validatsiyasi (000000, 404040 kabilarni rad etish) ─
    private fun isValidCode(code: String): Boolean {
        if (code.length != 6) return false
        if (code.all { it == code[0] }) return false                    // 000000, 111111
        if (code.substring(0, 3) == code.substring(3)) return false    // 404040, 123123
        val fake = setOf("000000","404040","123456","654321","999999","012345","111222","222333")
        return code !in fake
    }

    private fun stopPolling() { pollJob?.let { handler.removeCallbacks(it) }; pollJob = null }

    // ── Kodni paste ───────────────────────────────────────
    private fun pasteCode(code: String) {
        triedCodes.add(code)
        stopPolling()
        showToast("✓ Kod: $code")
        clipboard(code)
        val root  = rootInActiveWindow ?: run { resetState(); return }
        val nodes = allNodes(root)

        val focused = nodes.firstOrNull { it.isEditable && it.isFocused }
        if (focused != null) {
            setText(focused, code)
        } else {
            val editable = nodes.firstOrNull { it.isEditable && it.isEnabled }
            if (editable != null) {
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                handler.postDelayed({ setText(editable, code) }, 200)
            } else {
                nodes.firstOrNull { it.isFocused }?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
        }

        handler.postDelayed({ rootInActiveWindow?.let { clickNext(it) } }, 800)
        handler.postDelayed({ resetState() }, 5000)
    }

    private fun resetState() {
        state = State.IDLE; curEmail = ""; lastEmailFieldHash = 0
        seenIds.clear(); stopPolling()
    }

    private fun fullReset() {
        resetState(); preloadedEmail = ""; isPreloading = false; triedCodes.clear()
    }

    // ── Next tugmasi ──────────────────────────────────────
    private fun clickNext(root: AccessibilityNodeInfo) {
        val nodes = allNodes(root)

        // 1. Matn bo'yicha (Next, Continue, OK ...)
        for (text in NEXT_TEXTS) {
            val found = nodes.firstOrNull { n ->
                n.isClickable && n.isEnabled &&
                (n.text?.toString()?.equals(text, ignoreCase = true) == true ||
                 n.contentDescription?.toString()?.equals(text, ignoreCase = true) == true)
            }
            if (found != null) { found.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
        }

        // 2. Ekranning eng pastki-o'ng burchagidagi tugma (Telegram → FAB)
        val rect = Rect()
        val fab = nodes.filter { n ->
            n.isClickable && n.isEnabled && n.text.isNullOrEmpty()
        }.maxByOrNull { n ->
            n.getBoundsInScreen(rect)
            rect.right + rect.bottom
        }
        if (fab != null) { fab.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }

        // 3. Har qanday className bo'yicha ImageButton/FAB
        nodes.lastOrNull { n ->
            n.isClickable && n.isEnabled &&
            n.className?.let { it.contains("ImageView") || it.contains("Button") || it.contains("FloatingAction") } == true
        }?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ── Helpers ───────────────────────────────────────────
    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val b = Bundle()
        b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)
    }

    private fun clipboard(text: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("", text))
    }

    private fun showToast(msg: String) {
        handler.post { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show() }
    }

    private fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            list.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { walk(it) }
        }
        walk(root); return list
    }

    override fun onInterrupt() {}
    override fun onDestroy() { stopPolling(); super.onDestroy() }
}
