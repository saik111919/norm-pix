package com.normpix.cashflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms every persisted reminder's alarm after a reboot — [android.app.AlarmManager] alarms don't survive one. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val reminders = loadReminders(context) ?: return
        syncReminderAlarms(context, reminders)
    }
}
