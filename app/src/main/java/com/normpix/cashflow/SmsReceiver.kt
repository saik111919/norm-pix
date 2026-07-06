package com.normpix.cashflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

private const val PENDING_INBOX_PREFS = "cashflow_pending_inbox"
private const val PENDING_INBOX_KEY = "pending"

/** Persists a freshly parsed transaction (from SMS or, via [BankNotificationListener], a bank/UPI
 * notification) so [CashflowState] can pick it up next time it's active — the receiver/service
 * runs in its own short-lived context, with no access to the live app state. [idPrefix]
 * namespaces the two sources ("sms" / "ntf") so their generated ids can never collide in
 * [CashflowState.addParsedInboxEntry]'s cross-source dedup-by-id check. */
internal fun queuePendingInboxEntry(context: Context, sender: String, body: String, parsed: ParsedSms, idPrefix: String = "sms") {
    val prefs = context.getSharedPreferences(PENDING_INBOX_PREFS, Context.MODE_PRIVATE)
    val existing = runCatching { JSONArray(prefs.getString(PENDING_INBOX_KEY, "[]")) }.getOrDefault(JSONArray())
    val entry = JSONObject().apply {
        put("id", idPrefix + System.currentTimeMillis())
        put("sender", sender)
        put("sms", body)
        put("bank", parsed.bank.name)
        put("amount", parsed.amount)
        put("merchant", parsed.merchant ?: "")
        put("last4", parsed.last4 ?: "")
    }
    existing.put(entry)
    prefs.edit { putString(PENDING_INBOX_KEY, existing.toString()) }
}

/** Reads and clears any SMS-derived transactions queued since [CashflowState] last checked. */
fun drainPendingInboxEntries(context: Context): List<JSONObject> {
    val prefs = context.getSharedPreferences(PENDING_INBOX_PREFS, Context.MODE_PRIVATE)
    val raw = prefs.getString(PENDING_INBOX_KEY, null) ?: return emptyList()
    prefs.edit { remove(PENDING_INBOX_KEY) }
    val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return (0 until arr.length()).map { arr.getJSONObject(it) }
}

/**
 * Registered in the manifest. SMS_RECEIVED_ACTION is one of the few implicit broadcasts still
 * deliverable to manifest-declared receivers post-API 26, so this fires even when the app isn't
 * running, as long as RECEIVE_SMS is granted and the user hasn't turned SMS capture off.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        if (!hasSmsPermission(context)) return
        val prefs = context.getSharedPreferences("cashflow", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("smsOn", true)) return
        for (message in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
            val sender = message.originatingAddress ?: continue
            val body = message.messageBody ?: continue
            val parsed = parseSms(sender, body, sampleBanks()) ?: continue
            queuePendingInboxEntry(context, sender, body, parsed)
        }
    }
}
