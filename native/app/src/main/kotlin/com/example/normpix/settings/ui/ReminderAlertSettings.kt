package com.example.normpix.settings.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AppBlocking
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.reminders.BatteryAccess
import com.example.normpix.reminders.ExcludedApps
import com.example.normpix.reminders.InstalledApp
import com.example.normpix.reminders.ReminderAccess
import com.example.normpix.reminders.ReminderDelivery
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import androidx.compose.material.icons.outlined.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The tags the tests find the pieces of this page by. */
const val EXCLUDED_APPS_LIST_TAG = "excluded_apps_list"

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
    val usage = remember(revision) { ReminderAccess.hasUsageAccess(context) }

    fun open(intent: Intent?, fallback: Intent = ReminderAccess.appDetailsIntent(context)) {
        container.lock.expectExternalActivity()
        val first = intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Some makers' versions of a page do not take the app named in the request: the app's own page is the way to the same switch.
        if (first == null || runCatching { context.startActivity(first) }.isFailure) {
            runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

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
            "It never replaces the notification, and it never appears over an app you exclude.",
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
                                    "You choose the apps it stays away from next, and you can turn this off at any time.",
                                confirmLabel = "Open settings",
                                cancelLabel = "Not now",
                                icon = Icons.Outlined.Layers,
                            )
                            if (!go) return@launch
                            open(ReminderAccess.overlaySettingsIntent(context))
                        }
                        // The first time, banking, payment and password apps on this phone are pre-picked, so nothing private is covered.
                        if (prefs.reminderOverlayExcluded.isEmpty()) {
                            val apps = withContext(Dispatchers.Default) { ReminderAccess.launcherApps(context) }
                            preferences.setReminderOverlayExcluded(ExcludedApps.suggested(apps))
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
                    onClick = if (canDraw) null else ({ open(ReminderAccess.overlaySettingsIntent(context)) }),
                )
            }
            item {
                ListRow(
                    headline = "Usage access",
                    supporting = if (usage) {
                        "Allowed. Used only to see which app is open when a reminder comes due, so the card can stay away from the ones below. Nothing is kept."
                    } else if (prefs.reminderOverlayExcluded.isEmpty()) {
                        "Not allowed. Only needed to keep the card away from certain apps."
                    } else {
                        "Not allowed, so NormPix cannot tell when an excluded app is open and shows the notification only. Tap to allow it."
                    },
                    leading = { LeadingIcon(Icons.Outlined.Visibility) },
                    supportingMaxLines = 5,
                    onClick = if (usage) null else ({ open(ReminderAccess.usageAccessSettingsIntent(context)) }),
                )
            }
            item {
                ListRow(
                    headline = "Excluded apps",
                    supporting = when (val n = prefs.reminderOverlayExcluded.size) {
                        0 -> "None. The card can appear over any app."
                        1 -> "1 app. The card never appears over it."
                        else -> "$n apps. The card never appears over them."
                    },
                    leading = { LeadingIcon(Icons.Outlined.AppBlocking) },
                    onClick = {
                        scope.launch {
                            val apps = withContext(Dispatchers.Default) { ReminderAccess.launcherApps(context) }
                            val chosen = overlays.sheet<Set<String>>(expandable = true) { dismiss ->
                                ExcludedAppsSheet(apps = apps, initial = prefs.reminderOverlayExcluded, onDone = dismiss)
                            }
                            if (chosen != null) preferences.setReminderOverlayExcluded(chosen)
                        }
                    },
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

/**
 * The apps the floating card never appears over: every app with a launcher icon, searchable, each a checkbox. The ones a name
 * suggests are private (banking, payment, passwords) are listed first so they are easy to see, and the button keeps the choice.
 */
@Composable
fun ExcludedAppsSheet(apps: List<InstalledApp>, initial: Set<String>, onDone: (Set<String>?) -> Unit) {
    val haptics = rememberAppHaptics()
    var picked by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    val suggested = remember(apps) { ExcludedApps.suggested(apps) }
    val shown = remember(apps, query) {
        val matches = if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
        // Likely-private apps first, then the rest A to Z.
        matches.sortedWith(compareBy({ it.packageName !in suggested }, { it.label.lowercase() }))
    }
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.md)) {
            SheetHeader(
                title = "Excluded apps",
                subtitle = if (picked.isEmpty()) "The card can appear over any app" else "${picked.size} selected - the card never appears over them",
                trailing = if (picked.isNotEmpty()) {
                    { TextButton(onClick = { picked = emptySet() }) { Text("Clear") } }
                } else {
                    null
                },
            )
            Spacer(Modifier.height(AppSpacing.md))
            SearchField(value = query, onValueChange = { query = it }, placeholder = "Search apps")
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).testTag(EXCLUDED_APPS_LIST_TAG),
            contentPadding = PaddingValues(horizontal = AppSpacing.xxl, vertical = AppSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        ) {
            if (shown.isEmpty()) {
                item(key = "none") {
                    Text(
                        if (apps.isEmpty()) "No apps to list." else "No app matches \"${query.trim()}\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(AppSpacing.lg),
                    )
                }
            }
            items(shown, key = { it.packageName }) { app ->
                val checked = app.packageName in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .toggleable(value = checked, role = Role.Checkbox) {
                            haptics.selection()
                            picked = if (checked) picked - app.packageName else picked + app.packageName
                        }
                        .padding(vertical = AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.packageName)
                    Spacer(Modifier.width(AppSpacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (app.packageName in suggested) {
                            Text("Looks like a banking or private app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
        Button(
            onClick = { onDone(picked) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.lg),
        ) { Text("Save") }
    }
}

/** An app's own icon, made when it scrolls into view and drawn small. */
@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
    }
    val image = icon
    if (image != null) {
        Image(image, contentDescription = null, modifier = Modifier.size(40.dp))
    } else {
        LeadingIcon(Icons.Outlined.Notifications, size = 40.dp)
    }
}
