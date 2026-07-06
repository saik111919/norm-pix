package com.normpix.cashflow

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Notification access (needed for [BankNotificationListener] to read bank/UPI app notifications,
 * e.g. Google Pay/PhonePe payments that never send an SMS) is a special-access permission — there
 * is no runtime dialog for it, only a system Settings screen the user must toggle it on in
 * manually. [hasNotificationListenerPermission] is checked on every app resume (see
 * [CashflowState.refreshNotificationListenerPermission]) to notice when they've come back from it.
 */
fun hasNotificationListenerPermission(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Opens the system "Notification access" screen so the user can grant it manually. */
fun openNotificationListenerSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
