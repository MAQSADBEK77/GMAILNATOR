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
    private var lastEmailFieldId = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        showToast("Gmailnator Auto: faol")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!isTelegram(pkg)) return

        // Debounce — har event'da emas, 300ms keyin tekshirish
        handler.removeCallbacksAndMessages("check")
        handler.postAtTime({ checkScreen() }, "check", SystemClock.uptimeMillis() + 300)
    }

    private fun isTelegram(pkg: String) = pkg in listOf(
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.telegram.plus",
        "org.thunderdog.challegram"
    )

    private fun checkScreen() {
        val root = rootInActiveWindow ?: return

        // ── Email maydoni ────────────────────────────────
        val emailField = findEmailField(root)
        if (emailField != null) {
            val fieldId = emailField.viewIdResourceName ?: emailField.hashCode().toString()
            if (state == State.IDLE && fieldId != lastEmailFieldId) {
                lastEmailFieldId = fieldId
                handleEmail(emailField, root)
            }
            return
        }

        // ── Kod maydoni ──────────────────────────────────
        if (state == State.EMAIL_PASTED || state == State.POLLING) {
            val codeField = findCodeField(root)
            if (codeField != null && state == State.EMAIL_PASTED) {
                state = State.POLLING
                startPolling(codeField)
            }
        }
    }

    // ── Email maydonini topish ───────────────────────────
    private fun findEmailField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val emailKw = listOf("email", "e-mail", "pochta", "mail", "login email", "choose")

        // Ekranda "email" so'zi bormi? (Telegram "Choose a login email" yozuvi)
        val screenHasEmail = allNodes(root).any { n ->
            emailKw.any { kw ->
                n.text?.toString()?.lowercase()?.contains(kw) == true ||
                n.contentDescription?.toString()?.lowercase()?.contains(kw) == true
            }
        }

        if (!screenHasEmail) return null

        // Birinchi bo'sh tahrirlash maydonini qaytar
        return allNodes(root).firstOrNull { n ->
            n.isEditable && n.isEnabled && n.text.isNullOrEmpty()
        }
    }

    // ── Kod maydonini topish ─────────────────────────────
    private fun findCodeField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val codeKw = listOf("code", "kod", "otp", "verif", "digit", "raqam", "enter code")

        // Ekranda "code" so'zi bormi?
        val screenHasCode = allNodes(root).any { n ->
            codeKw.any { kw ->
                n.text?.toString()?.lowercase()?.contains(kw) == true ||
                n.contentDescription?.toString()?.lowercase()?.contains(kw) == true
            }
        }

        return allNodes(root).firstOrNull { n ->
            n.isEditable && n.isEnabled && n.text.isNullOrEmpty() &&
            (screenHasCode ||
             n.hintText?.toString()?.let { h ->
                 codeKw.any { h.lowercase().contains(it) }
             } == true ||
             n.inputType and android.text.InputType.TYPE_CLASS_NUMBER != 0)
        }
    }

    // ── Email yarat va paste qil ─────────────────────────
    private fun handleEmail(field: AccessibilityNodeInfo, root: AccessibilityNodeInfo) {
        state = State.EMAIL_PASTED  // preventive
        showToast("Email yaratilmoqda...")

        executor.execute {
            try {
                val email = Api.generateEmail()
                curEmail = email

                handler.post {
                    // Clipboard'ga ham qo'yish
                    clipboard(email)

                    // Maydonni tozalab email yozish
                    setText(field, email)

                    showToast("✓ Email paste: $email")

                    // 600ms kutib Next bosish
                    handler.postDelayed({
                        val r = rootInActiveWindow ?: return@postDelayed
                        clickNext(r)
                    }, 600)
                }
            } catch (e: Exception) {
                handler.post {
                    state = State.IDLE
                    showToast("✗ ${e.message}")
                }
            }
        }
    }

    // ── Kod polling ──────────────────────────────────────
    private fun startPolling(codeField: AccessibilityNodeInfo) {
        stopPolling()
        showToast("Kod kutilmoqda...")

        pollJob = object : Runnable {
            var attempts = 0
            override fun run() {
                attempts++
                if (attempts > 60) {   // 5 daqiqa timeout
                    handler.post { state = State.IDLE; showToast("Timeout — qayta urinib ko'ring") }
                    return
                }
                executor.execute {
                    try {
                        val msgs = Api.getInbox(curEmail)
                        if (msgs.isEmpty()) { handler.postDelayed(this, 5000); return@execute }

                        val content = Api.getMessage(msgs[0].id)
                        val code    = Api.extractCode(content)

                        if (code != null) {
                            handler.post { pasteCode(code) }
                        } else {
                            handler.postDelayed(this, 5000)
                        }
                    } catch (e: Exception) {
                        handler.postDelayed(this, 5000)
                    }
                }
            }
        }
        handler.postDelayed(pollJob!!, 4000)
    }

    private fun stopPolling() {
        pollJob?.let { handler.removeCallbacks(it) }
        pollJob = null
    }

    // ── Kodni paste + Next ───────────────────────────────
    private fun pasteCode(code: String) {
        showToast("✓ Kod topildi: $code")
        clipboard(code)

        val root = rootInActiveWindow
        val codeField = root?.let { findCodeField(it) }

        if (codeField != null) {
            setText(codeField, code)
            handler.postDelayed({
                val r = rootInActiveWindow ?: return@postDelayed
                clickNext(r)
            }, 600)
        }

        state = State.IDLE
        curEmail = ""
        lastEmailFieldId = ""
        stopPolling()
    }

    // ── "Next" / "Davom" tugmasini bosish ───────────────
    private fun clickNext(root: AccessibilityNodeInfo) {
        // Matn bo'yicha qidirish
        val candidates = listOf(
            "Next", "Далее", "Continue", "Done", "OK",
            "Keyingi", "Davom", "Send", "Отправить", "Verify"
        )
        for (text in candidates) {
            val nodes = root.findAccessibilityNodeInfosByText(text)
            val btn = nodes.firstOrNull { it.isClickable }
            if (btn != null) {
                btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return
            }
        }

        // IME "Done/Next" action
        val focused = allNodes(root).firstOrNull { it.isEditable && it.isFocused }
        focused?.performAction(AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)

        // Oxirgi imkon: ekranda eng quyi o'ng tugma
        val clickable = allNodes(root).filter { it.isClickable && it.isEnabled }
        clickable.lastOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    // ── Helpers ──────────────────────────────────────────
    private fun setText(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun clipboard(text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("", text))
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
            for (i in 0 until n.childCount) walk(n.getChild(i) ?: return)
        }
        walk(root)
        return list
    }

    override fun onInterrupt() {}
    override fun onDestroy() { stopPolling(); super.onDestroy() }
}
