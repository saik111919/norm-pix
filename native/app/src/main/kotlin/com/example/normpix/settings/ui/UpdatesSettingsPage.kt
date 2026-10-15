package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.NotificationsOff
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
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AppNotifications
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.BackgroundUpdates
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.updates.ui.updateSummary

/** The colour of the update rows (see [hue]). */
internal const val SettingsPageUpdatesHue = 6

/** One line on how NormPix keeps itself up to date, under Settings' "Automatic updates". */
fun automaticUpdatesSummary(prefs: AppPrefsState): String = when {
    !prefs.updateChecksEnabled -> "Off · only when you check"
    !prefs.backgroundUpdates.downloads -> "Daily · ${prefs.backgroundUpdates.short}"
    else -> "Daily · ${prefs.backgroundUpdates.short} · ${if (prefs.updateOnMobileData) "any network" else "Wi-Fi only"}"
}

/**
 * Settings > Automatic updates: whether NormPix checks GitHub once a day by itself (in the background, and when it is
 * opened), what it does with an update it finds - says so, downloads it and asks, or installs it - and whether a download
 * may use mobile data. Says when something on the phone stands in the way: Android's leave to install, or notifications
 * being off (then an update waits to be seen in the app).
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
                            supporting = "Once a day, in the background and when you open NormPix",
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
                    ListSection(footer = "An update is about the size of the app.") {
                        item {
                            SwitchRow(
                                headline = "Download on mobile data",
                                supporting = if (prefs.updateOnMobileData) "Updates download on any network" else "Updates wait for Wi-Fi",
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
        }
    }
}
