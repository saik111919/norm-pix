package com.example.normpix.wallet.expiry

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.normpix.settings.AppNotifications
import com.example.normpix.MainActivity
import com.example.normpix.NormPixApplication
import com.example.normpix.R
import com.example.normpix.reminders.ReminderNotifications
import java.time.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Notification channel, notification content and the intents behind its actions. */
object CardExpiryNotifications {
    const val CHANNEL_ID = "normpix_card_expiry"
    const val ACTION_ALARM = "com.example.normpix.CARD_EXPIRY_ALARM"
    const val ACTION_DONE = "com.example.normpix.CARD_EXPIRY_DONE"

    /** A tap on the notification asks the app to open on Wallet. */
    const val EXTRA_OPEN_WALLET = "open_wallet"
    const val EXTRA_CARD_ID = "card_id"

    /** One notification per card, told apart by tag, so two cards can never replace each other. */
    private const val NOTIFICATION_ID = 7301

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Card expiry", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A daily reminder while a saved card is about to expire, until you mark it done"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun enabled(context: Context): Boolean {
        if (!ReminderNotifications.hasPermission(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun uri(cardId: String, what: String): Uri = Uri.parse("normpix://card-expiry/$cardId/$what")

    fun alarmIntent(context: Context, cardId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, CardExpiryAlarmReceiver::class.java).setAction(ACTION_ALARM).setData(uri(cardId, "alarm")).putExtra(EXTRA_CARD_ID, cardId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun post(context: Context, record: CardExpiryRecord, expired: Boolean) {
        // Turned off in Settings: the card's expiry is still shown in Wallet, it just doesn't alert.
        if (!AppNotifications.allowed(context)) return
        createChannel(context)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setData(uri(record.cardId, "open"))
                .putExtra(EXTRA_OPEN_WALLET, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val done = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CardExpiryActionReceiver::class.java).setAction(ACTION_DONE).setData(uri(record.cardId, "done")).putExtra(EXTRA_CARD_ID, record.cardId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val valid = "%02d/%02d".format(record.expiry.monthValue, record.expiry.year % 100)
        // The lock screen shows only that something needs attention; the bank and last four digits wait behind the unlock.
        val lockScreen = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle("Card expiry reminder")
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(if (expired) "Card expired" else "Card expiring soon")
            .setContentText("${record.bank} •••• ${record.last4} ${if (expired) "expired" else "expires"} $valid")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreen)
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(0, "Mark as done", done)
            .build()
        NotificationManagerCompat.from(context).notify(record.cardId, NOTIFICATION_ID, notification)
    }

    fun cancelPosted(context: Context, cardId: String) {
        NotificationManagerCompat.from(context).cancel(cardId, NOTIFICATION_ID)
    }
}

/** One alarm per card, at its next reminder time. Inexact: a reminder a few minutes late is no worse, and needs no special permission. */
class AlarmExpiryScheduler(private val context: Context) : ExpiryScheduler {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    override fun schedule(cardId: String, atMillis: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, CardExpiryNotifications.alarmIntent(context, cardId))
    }

    override fun cancel(cardId: String) {
        alarms.cancel(CardExpiryNotifications.alarmIntent(context, cardId))
    }
}

class SystemExpiryNotifier(private val context: Context) : ExpiryNotifier {
    override fun areEnabled(): Boolean = CardExpiryNotifications.enabled(context)

    override fun show(record: CardExpiryRecord, expired: Boolean): Boolean {
        if (!areEnabled()) return false
        CardExpiryNotifications.post(context, record, expired)
        return true
    }

    override fun cancel(cardId: String) = CardExpiryNotifications.cancelPosted(context, cardId)
}

/** Records in their own preferences file (not the settings file, which "Reset settings" empties). */
class PrefsCardExpiryStore(private val prefs: SharedPreferences) : CardExpiryStore {
    override fun all(): Map<String, CardExpiryRecord> =
        prefs.all.entries.mapNotNull { (key, value) ->
            if (!key.startsWith(PREFIX) || value !is String) return@mapNotNull null
            runCatching { decode(Json.parseToJsonElement(value).jsonObject) }.getOrNull()?.let { it.cardId to it }
        }.toMap()

    override fun put(record: CardExpiryRecord) {
        prefs.edit().putString(PREFIX + record.cardId, encode(record).toString()).apply()
    }

    override fun remove(cardId: String) {
        prefs.edit().remove(PREFIX + cardId).apply()
    }

    private fun encode(r: CardExpiryRecord): JsonObject = buildJsonObject {
        put("cardId", r.cardId)
        put("bank", r.bank)
        put("last4", r.last4)
        put("expiry", r.expiry.toString())
        r.completedFor?.let { put("completedFor", it.toString()) }
        r.remindedAt?.let { put("remindedAt", it) }
    }

    private fun decode(json: JsonObject) = CardExpiryRecord(
        cardId = json.getValue("cardId").jsonPrimitive.content,
        bank = json.getValue("bank").jsonPrimitive.content,
        last4 = json.getValue("last4").jsonPrimitive.content,
        expiry = YearMonth.parse(json.getValue("expiry").jsonPrimitive.content),
        completedFor = (json["completedFor"] as? JsonPrimitive)?.content?.let(YearMonth::parse),
        remindedAt = (json["remindedAt"] as? JsonPrimitive)?.longOrNull,
    )

    private companion object {
        const val PREFIX = "card:"
    }
}

private fun Context.expiry(): CardExpiryTracker = (applicationContext as NormPixApplication).container.expiry

/** A card's alarm went off. */
class CardExpiryAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CardExpiryNotifications.EXTRA_CARD_ID) ?: return
        context.expiry().onAlarm(id)
    }
}

/** "Mark as done" on the notification. */
class CardExpiryActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(CardExpiryNotifications.EXTRA_CARD_ID) ?: return
        context.expiry().markDone(id)
    }
}

/** Alarms don't survive a reboot, an app update, or a change of clock or time zone: put them back. */
class CardExpiryBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
                context.expiry().rearmAll()
        }
    }
}
