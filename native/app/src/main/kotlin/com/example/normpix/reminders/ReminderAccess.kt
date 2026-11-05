package com.example.normpix.reminders

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
 * Everything here only reads the phone's state; nothing is stored. NormPix never looks at which other app is in front (no
 * "Usage access") and uses no Accessibility service, and nothing here gets around a restriction: each page opened is Android's
 * own switch for NormPix.
 */
object ReminderAccess {
    // --------------------------------------------------------------- Display over other apps

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Android's "Display over other apps", opened on NormPix by name: the page the SDK gives for it, with the package in the request,
     * which goes straight to NormPix's switch on the phones that honour it. Not all do: Android 17's Settings (an emulator) opens the
     * list of every app and ignores the package, and the one action that does open one app's page there
     * (`android.settings.MANAGE_APP_OVERLAY_PERMISSION`) is refused to other apps (it asks for the signature permission
     * `INTERNAL_SYSTEM_WINDOW`; seen in the log). So where the list opens, the person is told which row to tap ([OVERLAY_HINT]).
     */
    fun overlaySettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())

    /** What to tell the person on the way to Android's "Display over other apps", in case it opens on the list of apps. */
    const val OVERLAY_HINT = "Tap NormPix in the list, then turn on \"Allow display over other apps\"."


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
}
