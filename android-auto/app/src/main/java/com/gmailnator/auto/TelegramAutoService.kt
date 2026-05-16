package com.gmailnator.auto

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.os.*
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executors

class TelegramAutoService : AccessibilityService() {

    enum class State { IDLE, EMAIL_PASTED, POLLING }

    private var state = State.IDLE
    private var curEmail = ""
    private val executor = Executors.newSingleThreadExecutor()
    private val handler  = Handler(Looper.getMainLooper())
    private var pollJob: Runnable? = null
    private var lastEmailFieldHash = 0

    // ── Telegram paketlari ───────────────────────────────
    private val TELEGRAM_PKGS = setOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram"   // Telegram X
    )

    // ── Email ekrani kalit so'zlari (EN + RU + UZ + TG-X) ─
    private val EMAIL_KW = listOf(
        // English / Telegram X
        "email", "e-mail", "login email", "choose a login", "your email",
        "add email", "valid email", "protect your account",
        "enter your email address",   // Telegram X hint
        "enter email",
        // Russian
        "почт", "электронной", "укажите",
        "введите email", "выберите email", "ваш email",
        // Uzbek
        "emailingiz", "emailini", "pochta", "elektron", "email kiriting"
    )

    // ── Kod ekrani kalit so'zlari (EN + RU + UZ + TG-X) ──
    private val CODE_KW = listOf(
        // English / Telegram X
        "verification code", "confirmation code",   // Telegram X title
        "code", "verification", "login code",
        "enter code", "otp", "confirm", "check your email",
        "we've sent", "sent the code",              // Telegram X helper text
        // Russian
        "код", "введите код", "подтвер", "проверочн", "проверьте",
        // Uzbek
        "kod", "tasdiqlash", "kodni", "kiriting", "tekshiring"
    )

    companion object {
        const val ACTION_STOP    = "com.gmailnator.auto.STOP"
        const val ACTION_RESTART = "com.gmailnator.auto.RESTART"
    }

    // ── "Next" tugma matnlari (EN + RU + UZ) ─────────────
    private val NEXT_TEXTS = listOf(
        // English
        "Next", "Continue", "Done", "OK", "Send", "Submit", "Verify", "Confirm",
        // Russian
        "Далее", "Продолжить", "Готово", "ОК", "Отправить", "Подтвердить",
        // Uzbek
        "Keyingi", "Davom", "Tasdiqlash", "Yuborish", "OK", "Bajarish"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        showToast("Gmailnator Auto: faol ✓")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopPolling()
                resetState()
                showToast("⏹ To'xtatildi")
            }
            ACTION_RESTART -> {
                stopPolling()
                resetState()
                lastEmailFieldHash = 0
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

    // ── "Email not allowed" xato so'zlari ────────────────
    private val ERROR_KW = listOf(
        "not allowed", "not_allowed", "email_not_allowed", "email not allowed",
        "invalid email", "this email", "#400",
        "не разрешен", "не допускается", "недействительн",
        "ruxsat yo'q", "noto'g'ri email"
    )

    private fun checkScreen() {
        val root = rootInActiveWindow ?: return
        val nodes = allNodes(root)
        val texts = nodes.flatMap {
            listOfNotNull(
                it.text?.toString(),
                it.hintText?.toString(),
                it.contentDescription?.toString()
            )
        }.map { it.lowercase() }

        // ── "Email not allowed" xatosi ───────────────────
        val hasError = ERROR_KW.any { kw -> texts.any { it.contains(kw) } }
        if (hasError) {
            showToast("Email ruxsat yo'q — yangi email yoziladi...")
            resetState()
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastEmailFieldHash = 0
            handler.postDelayed({ checkScreen() }, 1000)
            return
        }

        // ── Email ekrani ─────────────────────────────────
        val isEmailScreen = EMAIL_KW.any { kw -> texts.any { it.contains(kw) } }
        if (isEmailScreen && state == State.IDLE) {
            // Eski email yozilgan bo'lsa — o'chirib yangi yozish
            val filledField = nodes.firstOrNull {
                (it.isEditable || it.isFocused) && it.isEnabled && !it.text.isNullOrEmpty()
            }
            if (filledField != null) {
                showToast("Eski email o'chirilmoqda...")
                setText(filledField, "")
                lastEmailFieldHash = 0
                handler.postDelayed({ checkScreen() }, 600)
                return
            }

            // Kengaytirilgan field qidirish
            val field = findEmailField(nodes)
            val dbg = if (field != null) field.className?.toString()?.substringAfterLast('.') ?: "found" else "NULL"
            showToast("Email ekran ✓ | field: $dbg | nodes: ${nodes.size}")

            if (field != null) {
                val h = System.identityHashCode(field)
                if (h != lastEmailFieldHash) {
                    lastEmailFieldHash = h
                    handleEmail(field, root)
                }
            }
            return
        }
        if (!isEmailScreen && state == State.IDLE) {
            showToast("Ekran: ${texts.take(2).joinToString("|")}")
        }

        // ── Kod ekrani ───────────────────────────────────
        val isCodeScreen = CODE_KW.any { kw -> texts.any { it.contains(kw) } }
        if (isCodeScreen && state == State.EMAIL_PASTED) {
            state = State.POLLING
            startPolling()
        }

        // ── Email ekrani ko'rinmasa, state reset ─────────
        if (!isEmailScreen && !isCodeScreen && state == State.IDLE) {
            lastEmailFieldHash = 0
        }
    }

    // ── Bo'sh tahrirlash maydoni ─────────────────────────
    private fun findEditableEmpty(nodes: List<AccessibilityNodeInfo>) =
        nodes.firstOrNull { it.isEditable && it.isEnabled && it.text.isNullOrEmpty() }

    // ── Email field (keng qidiruv) ────────────────────────
    private fun findEmailField(nodes: List<AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        // 1. Fokusdagi bo'sh maydon (eng ishonchli)
        nodes.firstOrNull { it.isFocused && it.isEnabled && it.text.isNullOrEmpty() }
            ?.let { return it }
        // 2. Tahrirlash mumkin bo'sh maydon
        nodes.firstOrNull { it.isEditable && it.isEnabled && it.text.isNullOrEmpty() }
            ?.let { return it }
        // 3. Fokusdagi har qanday maydon (ba'zi Telegram versiyalari)
        nodes.firstOrNull { it.isFocused && it.isEnabled }
            ?.let { return it }
        // 4. Har qanday tahrirlash maydoni
        nodes.firstOrNull { it.isEditable && it.isEnabled }
            ?.let { return it }
        return null
    }

    // ── Email yarat va paste ─────────────────────────────
    private fun handleEmail(field: AccessibilityNodeInfo, root: AccessibilityNodeInfo) {
        state = State.EMAIL_PASTED
        showToast("Email yaratilmoqda...")
        executor.execute {
            try {
                val email = Api.generateEmail()
                curEmail = email
                handler.post {
                    // Node recycled bo'lishi mumkin — qaytadan topamiz
                    val r = rootInActiveWindow
                    val freshField = r?.let { allNodes(it) }?.let { nodes ->
                        nodes.firstOrNull { it.isEditable && it.isEnabled && it.text.isNullOrEmpty() }
                        ?: nodes.firstOrNull { it.isEditable && it.isEnabled }
                    }
                    if (freshField != null) {
                        setText(freshField, email)
                    }
                    clipboard(email)
                    showToast("✓ $email")
                    handler.postDelayed({
                        rootInActiveWindow?.let { clickNext(it) }
                    }, 800)
                }
            } catch (e: Exception) {
                handler.post {
                    state = State.IDLE
                    lastEmailFieldHash = 0
                    showToast("✗ ${e.message}")
                }
            }
        }
    }

    // ── Kod polling ──────────────────────────────────────
    private fun startPolling() {
        stopPolling()
        showToast("Kod kutilmoqda...")
        var attempts = 0
        pollJob = object : Runnable {
            override fun run() {
                if (++attempts > 180) {  // 6 daqiqa (180 * 2s)
                    handler.post { state = State.IDLE; showToast("Timeout") }
                    return
                }
                executor.execute {
                    try {
                        val msgs = Api.getInbox(curEmail)
                        if (msgs.isEmpty()) { handler.postDelayed(this, 2000); return@execute }
                        val code = Api.extractCode(Api.getMessage(msgs[0].id))
                        if (code != null) {
                            handler.post { pasteCode(code) }
                        } else {
                            handler.postDelayed(this, 2000)
                        }
                    } catch (e: Exception) {
                        handler.postDelayed(this, 2000)
                    }
                }
            }
        }
        handler.postDelayed(pollJob!!, 3000)
    }

    private fun stopPolling() {
        pollJob?.let { handler.removeCallbacks(it) }
        pollJob = null
    }

    // ── Kodni paste + Next ───────────────────────────────
    private fun pasteCode(code: String) {
        showToast("✓ Kod: $code")
        clipboard(code)
        val root = rootInActiveWindow ?: run { resetState(); return }
        val nodes = allNodes(root)

        // 1. Fokusdagi tahrirlash maydoni
        val focused = nodes.firstOrNull { it.isEditable && it.isFocused }
        if (focused != null) {
            setText(focused, code)
        } else {
            // 2. Istalgan tahrirlash maydoni
            val editable = nodes.firstOrNull { it.isEditable && it.isEnabled }
            if (editable != null) {
                editable.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                handler.postDelayed({ setText(editable, code) }, 200)
            } else {
                // 3. OTP widget (alohida boxlar) — faqat clipboard paste
                val anyFocused = nodes.firstOrNull { it.isFocused }
                anyFocused?.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }
        }

        handler.postDelayed({
            rootInActiveWindow?.let { clickNext(it) }
        }, 800)
        handler.postDelayed({ resetState() }, 4000)
    }

    private fun resetState() {
        state = State.IDLE
        curEmail = ""
        lastEmailFieldHash = 0
        stopPolling()
    }

    // ── Next tugmasini bosish ────────────────────────────
    private fun clickNext(root: AccessibilityNodeInfo) {
        val nodes = allNodes(root)

        // 1. Matn bo'yicha (Next, Continue, OK, ...)
        for (text in NEXT_TEXTS) {
            val found = nodes.firstOrNull { n ->
                n.isClickable && n.isEnabled &&
                (n.text?.toString()?.equals(text, ignoreCase = true) == true ||
                 n.contentDescription?.toString()?.equals(text, ignoreCase = true) == true)
            }
            if (found != null) { found.performAction(AccessibilityNodeInfo.ACTION_CLICK); return }
        }

        // 2. IME action — klaviaturadagi ✓ / → tugmasi (eng ishonchli)
        val editFocused = nodes.firstOrNull { it.isEditable && it.isFocused }
        if (editFocused != null) {
            val b = Bundle()
            b.putInt("action", 6) // EditorInfo.IME_ACTION_NEXT
            if (editFocused.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, b)) return
            // fallback: generic IME action
            editFocused.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
        }

        // 3. FAB / ImageButton (Telegram'dagi ko'k o'q tugma)
        // lastOrNull — ekranning eng pastki o'ng elementini oladi
        val fab = nodes.filter { n ->
            n.isClickable && n.isEnabled && !n.text.isNullOrEmpty().let { hasText ->
                // ikonli tugmalar (matn yo'q yoki contentDesc bor)
                n.text.isNullOrEmpty() || n.contentDescription != null
            } && n.className?.let { cls ->
                cls.contains("ImageView") || cls.contains("Button") ||
                cls.contains("FloatingAction") || cls.contains("Image")
            } == true
        }
        fab.lastOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ── Helpers ──────────────────────────────────────────
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
        handler.post {
            android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun allNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val list = mutableListOf<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo) {
            list.add(n)
            for (i in 0 until n.childCount) { val c = n.getChild(i); if (c != null) walk(c) }
        }
        walk(root)
        return list
    }

    override fun onInterrupt() {}
    override fun onDestroy() { stopPolling(); super.onDestroy() }
}
