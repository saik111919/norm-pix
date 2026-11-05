package com.example.normpix.settings.ui

import android.content.Context
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.normpix.AppContainer
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.reminders.BatteryAccess
import com.example.normpix.reminders.ReminderAccess
import com.example.normpix.reminders.ReminderDelivery
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.rememberAppHaptics
import kotlinx.coroutines.launch

/**
 * Opens one of Android's own pages (a permission's switch for NormPix), telling the app lock that NormPix is being left on purpose
 * so it does not lock behind the person. Some makers' versions of a page do not take the app named in the request: then NormPix's
 * own page in Android's settings is the way to the same switch. A [hint] is said in a toast while the page is up - for a page that
 * may open on a list of apps, which row to tap. Used by Settings and by the welcome tour.
 */
fun openAndroidPage(container: AppContainer, context: Context, intent: Intent?, hint: String? = null) {
    container.lock.expectExternalActivity()
    val first = intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (first == null || runCatching { context.startActivity(first) }.isFailure) {
        runCatching { context.startActivity(ReminderAccess.appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    if (hint != null) android.widget.Toast.makeText(context.applicationContext, hint, android.widget.Toast.LENGTH_LONG).show()
}

/**
 * The reminder part of Settings > Notifications: how sure a reminder is to reach the person (exact alarms, a full-screen alert
 * over a locked phone, the battery), and the optional floating card over other apps. Notifications stay the default; each row
 * says in words whether it is working and, where it is not, opens Android's own page to put it right. Nothing here works around
 * an Android restriction or uses an Accessibility service.
 */
@Composable
fun ReminderAlertSettings() {
    val container = LocalAppContainer.current
    val preferences = container.preferences
    val prefs by preferences.state.collectAsState()
    val context = LocalContext.current
    val overlays = LocalOverlays.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    // Android's switches are changed on its own pages, so they are read again each time this page comes back.
    var revision by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { revision++ }
    val exact = remember(revision) { container.alarms?.canScheduleExact() ?: true }
    val fullScreenAllowed = remember(revision) { ReminderAccess.canUseFullScreen(context) }
    val battery = remember(revision) { ReminderAccess.battery(context) }
    val canDraw = remember(revision) { ReminderAccess.canDrawOverlays(context) }

    fun open(intent: Intent?) = openAndroidPage(container, context, intent)
    fun openOverlayPage() = openAndroidPage(container, context, ReminderAccess.overlaySettingsIntent(context), ReminderAccess.OVERLAY_HINT)

    ListSection(
        title = "Reaching you on time",
        footer = "Notifications are how reminders reach you. These make sure Android lets them ring at the right minute, even when the phone is idle.",
    ) {
        item {
            ListRow(
                headline = "Exact alarms",
                supporting = if (exact) {
                    "On. Reminders ring at the exact minute."
                } else {
                    "Off. Reminders can arrive a few minutes late. Tap to allow \"Alarms & reminders\" for NormPix."
                },
                leading = { LeadingIcon(Icons.Outlined.Alarm) },
                supportingMaxLines = 4,
                onClick = if (exact) null else ({ open(container.alarms?.exactAlarmSettingsIntent()) }),
            )
        }
        item {
            ListRow(
                headline = "Battery",
                supporting = when (battery) {
                    BatteryAccess.Unrestricted -> "Unrestricted. Nothing holds reminders back."
                    BatteryAccess.Optimized -> "Optimised. Reminders ring, but a phone's own app-killer may stop them. Tap to choose \"Unrestricted\" for NormPix."
                    BatteryAccess.Restricted -> "Restricted in the background: reminders will not ring. Tap to change it to Unrestricted."
                },
                leading = { LeadingIcon(Icons.Outlined.BatteryChargingFull) },
                supportingMaxLines = 4,
                onClick = if (battery == BatteryAccess.Unrestricted) null else ({ open(ReminderAccess.batterySettingsIntent()) }),
            )
        }
        item {
            SettingsSwitchRow(
                headline = "Full-screen alert",
                supporting = when {
                    !prefs.reminderFullScreenEnabled -> "Off. A due reminder is a notification, even on a locked phone."
                    fullScreenAllowed -> "A due reminder lights a locked phone with its own screen."
                    else -> "Android has not allowed this, so a locked phone gets the notification instead. Allow it below."
                },
                icon = Icons.Outlined.Fullscreen,
                hue = SettingsPageNotificationsHue,
                checked = prefs.reminderFullScreenEnabled,
                onCheckedChange = {
                    haptics.selection()
                    preferences.setReminderFullScreenEnabled(it)
                },
            )
        }
        if (prefs.reminderFullScreenEnabled && !fullScreenAllowed && ReminderAccess.fullScreenSettingsIntent(context) != null) {
            item {
                ListRow(
                    headline = "Allow full-screen alerts in Android",
                    supporting = "Opens \"Full-screen notifications\" for NormPix",
                    leading = { LeadingIcon(Icons.Outlined.Fullscreen) },
                    onClick = { open(ReminderAccess.fullScreenSettingsIntent(context)) },
                )
            }
        }
    }

    ListSection(
        title = "Floating reminders",
        footer = "A small card over the app you are using, while your phone is unlocked - with Mark as paid, Snooze, Dismiss and Open. " +
            "It never replaces the notification. NormPix does not look at which app that is.",
    ) {
        item {
            SettingsSwitchRow(
                headline = "Show over other apps",
                supporting = if (prefs.reminderOverlayEnabled) "On" else "Off",
                icon = Icons.Outlined.Layers,
                hue = SettingsPageNotificationsHue,
                checked = prefs.reminderOverlayEnabled,
                onCheckedChange = { on ->
                    haptics.selection()
                    if (!on) {
                        preferences.setReminderOverlayEnabled(false)
                        return@SettingsSwitchRow
                    }
                    scope.launch {
                        if (!canDraw) {
                            // Asked and explained first: the system page alone does not say what NormPix wants it for.
                            val go = overlays.confirm(
                                title = "Allow display over other apps?",
                                message = "Android needs your OK for NormPix to show a reminder card over the app you are using. " +
                                    "It is only the card: NormPix does not see what is on the screen, and you can turn this off at any time. " +
                                    "On the next screen, tap NormPix and turn on \"Allow display over other apps\".",
                                confirmLabel = "Open settings",
                                cancelLabel = "Not now",
                                icon = Icons.Outlined.Layers,
                            )
                            if (!go) return@launch
                            openOverlayPage()
                        }
                        preferences.setReminderOverlayEnabled(true)
                    }
                },
            )
        }
        if (prefs.reminderOverlayEnabled) {
            item {
                ListRow(
                    headline = "Display over other apps",
                    supporting = if (canDraw) "Allowed" else "Not allowed yet. Tap to allow it for NormPix in Android's settings.",
                    leading = { LeadingIcon(Icons.Outlined.Layers) },
                    supportingMaxLines = 4,
                    onClick = if (canDraw) null else ({ openOverlayPage() }),
                )
            }
            item {
                ListRow(
                    headline = "Try it",
                    supporting = if (canDraw) "Show a sample card now" else "Allow display over other apps first",
                    leading = { LeadingIcon(Icons.Rounded.PlayArrow) },
                    enabled = canDraw,
                    onClick = if (canDraw) ({ ReminderDelivery.showSample(context) }) else null,
                )
            }
        }
    }
}
