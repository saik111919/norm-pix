package com.example.normpix.reminders

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.net.toUri

/** How freely Android lets NormPix run in the background, which decides whether a reminder's alarm is allowed to ring. */
enum class BatteryAccess {
    /** Exempt from battery optimisation: alarms ring on time even in Doze. */
    Unrestricted,

    /** The default: exact alarms still ring, but a phone's own "app killer" may stop them. */
    Optimized,

    /** The user (or the phone) restricted NormPix in the background: its alarms will not ring. */
    Restricted,
}

/**
 * What Android says about the permissions and states the reminders depend on, and the system pages where each is changed.
 * Everything here only reads the phone's state; nothing is stored, and what app is in front is looked at only when a reminder
 * comes due and never kept (see [foregroundPackage]).
 *
 * No Accessibility service is used or needed, and nothing here gets around a restriction: each page opened is Android's own
 * switch for NormPix.
 */
object ReminderAccess {
    // --------------------------------------------------------------- Display over other apps

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Android's page for "Display over other apps", opened on NormPix. */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    // --------------------------------------------------------------- Which app is in front (Usage access)

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Android's "Usage access" page. Some makers' versions ignore the package and open the list. */
    fun usageAccessSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, "package:${context.packageName}".toUri())

    /**
     * The app in front right now, read once from Android's usage events (what the system already keeps), or null when it cannot be
     * told: no Usage access, or no app has changed in the last day. It is used for one thing - not showing the floating card over an
     * app the user excluded - and is neither stored nor logged.
     */
    fun foregroundPackage(context: Context, now: Long = System.currentTimeMillis()): String? {
        if (!hasUsageAccess(context)) return null
        val stats = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { stats.queryEvents(now - EVENT_WINDOW_MS, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var front: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                // ACTIVITY_RESUMED / MOVE_TO_FOREGROUND are one value (1), ACTIVITY_PAUSED / MOVE_TO_BACKGROUND another (2).
                RESUMED -> front = event.packageName
                PAUSED -> if (event.packageName == front) front = null
            }
        }
        return front
    }

    private const val RESUMED = 1
    private const val PAUSED = 2
    private const val EVENT_WINDOW_MS = 24L * 60 * 60 * 1000

    // --------------------------------------------------------------- Screen and lock

    fun screenOn(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isInteractive == true

    /** Behind the lock screen (or the screen is showing it). */
    fun locked(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != false

    // --------------------------------------------------------------- Full-screen alerts

    /**
     * Whether Android lets NormPix raise a full-screen alert from a notification. Before Android 14 the permission the manifest
     * declares is granted at install; from 14 the user may take it away, and a new install may not have it.
     */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true

    /** Android 14's page for "Full-screen notifications", or null where there is no such switch. */
    fun fullScreenSettingsIntent(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${context.packageName}".toUri())
        } else {
            null
        }

    // --------------------------------------------------------------- Battery

    fun battery(context: Context): BatteryAccess {
        val power = context.getSystemService(PowerManager::class.java)
        if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) return BatteryAccess.Unrestricted
        val activity = context.getSystemService(ActivityManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity?.isBackgroundRestricted == true) return BatteryAccess.Restricted
        return BatteryAccess.Optimized
    }

    /** The list of apps exempt from battery optimisation (it needs no permission to open; NormPix is found in it). */
    fun batterySettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /** NormPix's own page in Android's settings, where a phone's battery controls for it live. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    // --------------------------------------------------------------- Apps that can be excluded

    /** The apps with a launcher icon on this phone (the ones a person would think of as "an app"), NormPix itself left out. */
    fun launcherApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching { pm.queryIntentActivities(main, 0) }.getOrDefault(emptyList())
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
