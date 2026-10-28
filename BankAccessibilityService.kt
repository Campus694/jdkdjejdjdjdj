package com.example.paymenthelper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.regex.Pattern

class BankAccessibilityService : AccessibilityService() {
    private var checkPackage: String? = null
    private var startedAt: Long = 0L
    private var returned = false

    companion object {
        @Volatile private var activePackage: String? = null
        @Volatile private var checkStartedAt: Long = 0L

        fun beginCheck(context: Context, packageName: String) {
            activePackage = packageName
            checkStartedAt = SystemClock.elapsedRealtime()
            context.getSharedPreferences("payment_helper", Context.MODE_PRIVATE)
                .edit().putLong("check_started_at", System.currentTimeMillis()).apply()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        checkPackage = activePackage
        startedAt = checkStartedAt
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val target = activePackage ?: checkPackage ?: return
        if (checkStartedAt == 0L || returned) return

        val eventPackage = event.packageName?.toString()
        val elapsed = SystemClock.elapsedRealtime() - checkStartedAt
        if (elapsed > 30_000L) {
            finishCheck(null, target)
            return
        }

        // Only inspect the package explicitly selected by the user.
        if (eventPackage == target) {
            checkPackage = target
            val root = rootInActiveWindow
            val texts = mutableListOf<String>()
            collectVisibleText(root, texts, 0)
            val balance = extractBalance(texts)
            if (balance != null) finishCheck(balance, target)
        }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 20 || out.size > 500) return
        if (node.isVisibleToUser) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
            node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
        for (i in 0 until node.childCount) collectVisibleText(node.getChild(i), out, depth + 1)
    }

    private fun extractBalance(texts: List<String>): String? {
        val fullText = texts.joinToString(" | ")
        val labelPattern = Pattern.compile(
            "(?i)(?:баланс|доступно|доступный\\s+остаток|остаток|available|balance)\\D{0,45}" +
            "((?:\\d{1,3}(?:[\\s\\u00A0.,']\\d{3})+|\\d+)(?:[.,]\\d{2})?\\s*(?:₽|руб(?:\\.)?|BYN|бел\\.\\s*руб\\.?|€|\\$|USD|EUR)?)"
        )
        val matcher = labelPattern.matcher(fullText)
        if (matcher.find()) return matcher.group(1)?.trim()

        // Conservative fallback: accept currency-tagged amounts only, avoiding arbitrary numbers.
        val amountPattern = Pattern.compile(
            "(?i)(\\d{1,3}(?:[\\s\\u00A0]\\d{3})*(?:[.,]\\d{2})?\\s*(?:₽|руб(?:\\.)?|BYN|€|\\$|USD|EUR))"
        )
        val amountMatcher = amountPattern.matcher(fullText)
        val candidates = mutableListOf<String>()
        while (amountMatcher.find() && candidates.size < 10) candidates.add(amountMatcher.group(1).trim())
        return candidates.firstOrNull()
    }

    private fun finishCheck(balance: String?, targetPackage: String) {
        if (returned) return
        returned = true
        val prefs = getSharedPreferences("payment_helper", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("last_balance", balance)
            .putLong("last_balance_time", System.currentTimeMillis())
            .apply()

        // Return to Payment Helper after reading or timeout. Do not interact with the bank UI.
        val ownIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (ownIntent != null) {
            ownIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            try { startActivity(ownIntent) } catch (_: Exception) { }
        }
        activePackage = null
        checkPackage = null
        checkStartedAt = 0L
    }

    override fun onInterrupt() = Unit
}
