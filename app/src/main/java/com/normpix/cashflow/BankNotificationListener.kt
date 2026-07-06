package com.normpix.cashflow

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Registered in the manifest. Catches bank/UPI app notifications (e.g. Google Pay, PhonePe —
 * apps that often push a notification with no SMS at all for a payment) the same way
 * [SmsReceiver] catches SMS: parse against [sampleBanks], queue any match for
 * [CashflowState.refreshSmsInbox] to pick up next time the app is active. Requires the user to
 * grant "Notification access" manually via system Settings — see [hasNotificationListenerPermission].
 */
class BankNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return // ignore our own posted notifications
        val prefs = getSharedPreferences("cashflow", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifCaptureOn", true)) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = "$title $text".trim()
        if (body.isBlank()) return

        val parsed = parseSms(sbn.packageName, body, sampleBanks()) ?: return
        queuePendingInboxEntry(this, sbn.packageName, body, parsed, idPrefix = "ntf")
    }
}
