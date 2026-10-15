package com.example.normpix.settings

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.normpix.NormPixApplication

/** The user's own switch for NormPix's notifications (Settings > Notifications), on top of Android's. */
object AppNotifications {
    /** Whether the user lets NormPix post notifications; true when that can't be told (the app is still starting). */
    fun allowed(context: Context): Boolean = runCatching {
        (context.applicationContext as? NormPixApplication)?.container?.preferences?.state?.value?.notificationsEnabled
    }.getOrNull() ?: true

    /** Whether Android lets NormPix post notifications at all (its permission and its own per-app switch). */
    fun allowedByAndroid(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Takes down every notification NormPix has showing: they were turned off, so none should be left behind. */
    fun withdrawAll(context: Context) {
        NotificationManagerCompat.from(context).cancelAll()
    }
}
