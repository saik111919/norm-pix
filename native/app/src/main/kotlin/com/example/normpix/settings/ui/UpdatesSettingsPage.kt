package com.example.normpix.settings.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.normpix.availability.formatTime
import com.example.normpix.finance.ui.TimePickerDialogFor
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AppNotifications
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.settings.UpdateCheckEvery
import com.example.normpix.settings.UpdateCheckWhen
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.UpdateCheckTimes
import com.example.normpix.updates.ui.updateSummary
import java.time.LocalDate
import java.time.LocalTime

/** The colour of the update rows (see [hue]). */
internal const val SettingsPageUpdatesHue = 6

/**
 * One line on how NormPix keeps itself up to date, under Settings' "Automatic updates": how often (and when, if at a set
 * time), what it does with an update, and the network a download may use. [is24Hour] is how the phone writes the time.
 */
fun automaticUpdatesSummary(prefs: AppPrefsState, is24Hour: Boolean = false): String {
    if (!prefs.updateChecksEnabled) return "Off · only when you check"
    val mode = prefs.backgroundUpdates
    return listOfNotNull(
        updateCheckCadence(prefs, is24Hour),
        mode.short,
        if (mode.downloads) (if (prefs.updateOnMobileData) "any network" else "Wi-Fi only") else null,
    ).joinToString(" · ")
}

/** "Daily", or "Weekly at 9:00 PM" when the check is made at a set time ("Twice daily at 9:00 AM and 9:00 PM"). */
internal fun updateCheckCadence(prefs: AppPrefsState, is24Hour: Boolean): String {
    val how = prefs.updateCheckEvery.short
    if (prefs.updateCheckWhen != UpdateCheckWhen.AtSetTime) return how
    return "$how at ${updateCheckTimesText(prefs, is24Hour)}"
}

/** The time of day the check is made around ("9:00 PM"), and for twice a day both ("9:00 AM and 9:00 PM"). */
internal fun updateCheckTimesText(prefs: AppPrefsState, is24Hour: Boolean): String =
    UpdateCheckTimes.minutesOfDay(prefs.updateCheckEvery, prefs.updateCheckMinuteOfDay)
        .joinToString(" and ") { formatTime(LocalTime.of(it / 60, it % 60), is24Hour) }

/** What the "Check for updates automatically" switch says it does: how often, and in the background and/or on opening. */
internal fun automaticChecksSupporting(prefs: AppPrefsState): String =
    "${prefs.updateCheckEvery.label}, in the background" + if (prefs.updateCheckOnOpen) " and when you open NormPix" else ""

/**
 * Settings > Automatic updates: whether NormPix checks GitHub by itself (in the background, and when it is opened), how
 * often and at what time of day, what it does with an update it finds - says so, downloads it and asks, or installs it - and
 * whether a background download may use mobile data. Says when something on the phone stands in the way: Android's leave to
 * install, or notifications being off (then an update waits to be seen in the app).
 *
 * Every group after the first has a title: a list section adds no space above itself without one, and an untitled group would
 * sit tight under whatever is above it.
 */
@Composable
fun UpdatesSettingsPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val preferences = container.preferences
    val prefs by preferences.state.collectAsState()
    val updater = container.updates
    val system = rememberSettingsSystemActions()
    val haptics = rememberAppHaptics()
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    var pickingTime by remember { mutableStateOf(false) }
    // Read again whenever the page comes back: both are changed in Android's settings.
    var notificationsShown by remember { mutableStateOf(AppNotifications.allowedByAndroid(context)) }
    var installsAllowed by remember { mutableStateOf(updater?.installsAllowed ?: false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsShown = AppNotifications.allowedByAndroid(context)
        installsAllowed = updater?.installsAllowed ?: false
    }

    AppSubPage(title = "Automatic updates", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                if (updater == null || !updater.enabled) {
                    ListSection(footer = "Only the release build of NormPix updates itself from GitHub.") {
                        item {
                            ListRow(
                                headline = "Updates come with release builds",
                                leading = { LeadingIcon(Icons.Outlined.UpdateDisabled) },
                            )
                        }
                    }
                    return@Column
                }
                ListSection(
                    footer = "Only GitHub is contacted. Every update is checked against its release, and Android installs it " +
                        "only if it's signed with the same key as the NormPix on this phone. Your data stays as it is.",
                ) {
                    item {
                        SettingsSwitchRow(
                            headline = "Check for updates automatically",
                            supporting = automaticChecksSupporting(prefs),
                            icon = Icons.Outlined.Update,
                            hue = SettingsPageUpdatesHue,
                            checked = prefs.updateChecksEnabled,
                            onCheckedChange = { on ->
                                haptics.selection()
                                preferences.setUpdateChecksEnabled(on)
                            },
                        )
                    }
                    item {
                        val update by updater.state.collectAsState()
                        SettingsRow(
                            headline = "Check now",
                            supporting = updateSummary(update, updater),
                            icon = Icons.Outlined.SystemUpdate,
                            hue = SettingsPageUpdatesHue,
                            onClick = { system.checkForUpdates() },
                        )
                    }
                }
                if (!prefs.updateChecksEnabled) return@Column

                ListSection(title = "How often", modifier = Modifier.selectableGroup()) {
                    for (choice in UpdateCheckEvery.entries) {
                        item {
                            RadioRow(
                                headline = choice.label,
                                selected = prefs.updateCheckEvery == choice,
                                onClick = {
                                    if (prefs.updateCheckEvery != choice) {
                                        haptics.selection()
                                        preferences.setUpdateCheckEvery(choice)
                                    }
                                },
                            )
                        }
                    }
                }
                ListSection(title = "When", modifier = Modifier.selectableGroup()) {
                    for (choice in UpdateCheckWhen.entries) {
                        item {
                            RadioRow(
                                headline = choice.label,
                                supporting = choice.description,
                                selected = prefs.updateCheckWhen == choice,
                                onClick = {
                                    if (prefs.updateCheckWhen != choice) {
                                        haptics.selection()
                                        preferences.setUpdateCheckWhen(choice)
                                    }
                                },
                            )
                        }
                    }
                }
                RevealVisibility(visible = prefs.updateCheckWhen == UpdateCheckWhen.AtSetTime) {
                    val twice = if (prefs.updateCheckEvery == UpdateCheckEvery.TwiceADay) "Twice a day is this time and 12 hours later. " else ""
                    ListSection(
                        title = "Check time",
                        footer = twice + "NormPix checks around it, once the phone is on a network that suits the download and " +
                            "its battery isn't low. Android may start it a little late.",
                    ) {
                        item {
                            ListRow(
                                headline = "Time",
                                supporting = updateCheckTimesText(prefs, is24Hour),
                                leading = { LeadingIcon(Icons.Outlined.Schedule) },
                                onClick = { pickingTime = true },
                            )
                        }
                    }
                }
                ListSection(title = "In the app") {
                    item {
                        SwitchRow(
                            headline = "Check when you open NormPix",
                            supporting = if (prefs.updateCheckOnOpen) {
                                "A quiet check, at most ${prefs.updateCheckEvery.label.lowercase()}"
                            } else {
                                "Only the check in the background looks for updates"
                            },
                            checked = prefs.updateCheckOnOpen,
                            onCheckedChange = { on ->
                                haptics.selection()
                                preferences.setUpdateCheckOnOpen(on)
                            },
                            leading = { LeadingIcon(Icons.Outlined.PhoneAndroid) },
                        )
                    }
                }

                val mode = prefs.backgroundUpdates
                ListSection(
                    title = "When an update is found",
                    modifier = Modifier.selectableGroup(),
                    footer = when {
                        mode != BackgroundUpdates.Install -> null
                        !updater.installsQuietly ->
                            "This phone's Android asks before every install, so an update waits for your Install, as with " +
                                "\"${BackgroundUpdates.Download.label}\"."
                        else -> "It installs once NormPix has been closed for a while, never while you're using it. NormPix " +
                            "closes as it updates. If Android wants you to confirm one, it waits for your Install instead."
                    },
                ) {
                    for (choice in BackgroundUpdates.entries) {
                        item {
                            RadioRow(
                                headline = choice.label,
                                supporting = choice.description,
                                selected = mode == choice,
                                onClick = {
                                    if (mode != choice) {
                                        haptics.selection()
                                        preferences.setBackgroundUpdates(choice)
                                    }
                                },
                            )
                        }
                    }
                }
                if (mode.downloads) {
                    ListSection(
                        title = "Network",
                        footer = "Only for downloads NormPix starts by itself; one you start yourself uses any network. A metered " +
                            "Wi-Fi, such as a hotspot, counts as mobile data.",
                    ) {
                        item {
                            SwitchRow(
                                headline = "Download on mobile data",
                                supporting = if (prefs.updateOnMobileData) {
                                    "Background downloads may use mobile data"
                                } else {
                                    "Background downloads wait for Wi-Fi"
                                },
                                checked = prefs.updateOnMobileData,
                                onCheckedChange = { on ->
                                    haptics.selection()
                                    preferences.setUpdateOnMobileData(on)
                                },
                                leading = { LeadingIcon(Icons.Outlined.SignalCellularAlt) },
                            )
                        }
                    }
                }
                if (mode == BackgroundUpdates.Install && updater.installsQuietly && !installsAllowed) {
                    ListSection(
                        title = "Android's leave",
                        footer = "Android asks this once. Without it, an update waits for your Install.",
                    ) {
                        item {
                            ListRow(
                                headline = "Allow NormPix to install updates",
                                supporting = "Turn on \"Allow from this source\" for NormPix",
                                leading = { LeadingIcon(Icons.Outlined.InstallMobile) },
                                onClick = { updater.openInstallSetting() },
                            )
                        }
                    }
                }
                if (!prefs.notificationsEnabled || !notificationsShown) {
                    ListSection(
                        title = "Notifications are off",
                        footer = "Updates are still found, and offered when you open NormPix.",
                    ) {
                        item {
                            ListRow(
                                headline = "Notifications",
                                supporting = "Turn them on to hear about updates",
                                leading = { LeadingIcon(Icons.Outlined.NotificationsOff) },
                                onClick = { navigator.openSettings(SettingsPage.Notifications) },
                            )
                        }
                    }
                }
            }
            if (pickingTime) {
                TimePickerDialogFor(
                    initial = LocalDate.now().atTime(prefs.updateCheckTime),
                    onPicked = { hour, minute ->
                        preferences.setUpdateCheckTime(hour, minute)
                        pickingTime = false
                    },
                    onDismiss = { pickingTime = false },
                )
            }
        }
    }
}
