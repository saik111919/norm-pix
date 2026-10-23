package com.example.normpix.wallet.clipboard

import androidx.core.content.edit
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PersistableBundle
import com.example.normpix.NormPixApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The real clipboard. A clip is marked sensitive (no preview, kept out of keyboard suggestions) and tagged with its copy's token. */
class AndroidClipboardPort(context: Context) : ClipboardPort {
    private val manager = context.getSystemService(ClipboardManager::class.java)

    override fun write(label: String, value: String, token: String) {
        val clip = ClipData.newPlainText(label, value)
        clip.description.extras = PersistableBundle().apply {
            // The literal, not ClipDescription.EXTRA_IS_SENSITIVE: older systems simply ignore it.
            putBoolean(EXTRA_IS_SENSITIVE, true)
            putString(EXTRA_TOKEN, token)
        }
        manager.setPrimaryClip(clip)
    }

    /**
     * Reads only the clip's description - never its text - so no copied detail is read back, and Android's
     * "NormPix pasted from your clipboard" notice never appears. A background app gets no description at all.
     */
    override fun owner(): ClipboardOwner {
        val description = manager.primaryClipDescription ?: return ClipboardOwner.Unknown
        val token = description.extras?.getString(EXTRA_TOKEN)
        return if (token != null) ClipboardOwner.NormPix(token) else ClipboardOwner.Someone
    }

    override fun clear() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.clearPrimaryClip()
        } else {
            manager.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    private companion object {
        const val EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"
        const val EXTRA_TOKEN = "com.example.normpix.COPY_TOKEN"
    }
}

/** Remembers the pending copy (token and deadline only) in its own preferences file, so "reset settings" can't lose it. */
class PrefsPendingClearStore(private val prefs: SharedPreferences) : PendingClearStore {
    override fun load(): PendingClear? {
        val token = prefs.getString(K_TOKEN, null) ?: return null
        return PendingClear(token, prefs.getLong(K_DEADLINE, 0L))
    }

    override fun save(pending: PendingClear?) {
        prefs.edit {
            if (pending == null) {
                remove(K_TOKEN).remove(K_DEADLINE)
            } else {
                putString(K_TOKEN, pending.token).putLong(K_DEADLINE, pending.deadlineMillis)
            }
        }
    }

    private companion object {
        const val K_TOKEN = "token"
        const val K_DEADLINE = "deadline"
    }
}

/**
 * An alarm for the case where the process is gone when the time is up. Inexact on purpose: it is a safety net
 * behind the in-process timer, and an exact alarm would need a permission the user may have withheld.
 */
class AlarmClearBackup(private val context: Context) : ClearBackup {
    private val alarms get() = context.getSystemService(AlarmManager::class.java)

    private fun intent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ClipboardClearReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun schedule(atMillis: Long) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent())
    }

    override fun cancel() {
        alarms.cancel(intent())
    }

    private companion object {
        const val ACTION = "com.example.normpix.CLIPBOARD_CLEAR"
    }
}

/** The backup alarm went off: finish clearing whatever is still pending. */
class ClipboardClearReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as? NormPixApplication)?.container?.clipboard?.reconcile()
    }
}

/** The guard wired to the real clipboard, the app's own timers, the pending-copy file and the backup alarm. */
fun androidSecureClipboard(context: Context): SecureClipboard {
    val app = context.applicationContext
    return ClipboardGuard(
        port = AndroidClipboardPort(app),
        store = PrefsPendingClearStore(app.getSharedPreferences("normpix_clipboard", Context.MODE_PRIVATE)),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        backup = AlarmClearBackup(app),
    )
}
