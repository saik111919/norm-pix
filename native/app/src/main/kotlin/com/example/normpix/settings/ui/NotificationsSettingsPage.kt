package com.example.normpix.settings.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Settings
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
import com.example.normpix.settings.AppNotifications
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing

/**
 * Settings > Notifications: one switch for everything NormPix posts - reminders as they come due, the daily card-expiry
 * reminder, and word of an update. Off, nothing alerts; reminders, expiry dates and updates are still there in the app. Android's own switch for the app is
 * a separate one, and this page says when that is what keeps notifications away.
 */
@Composable
fun NotificationsSettingsPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val preferences = container.preferences
    val prefs by preferences.state.collectAsState()
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val snackbars = LocalSnackbars.current
    // Read again whenever the page comes back, which is how a change made in Android's settings shows up here.
    var androidAllows by remember { mutableStateOf(AppNotifications.allowedByAndroid(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { androidAllows = AppNotifications.allowedByAndroid(context) }

    fun openAndroidSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        container.lock.expectExternalActivity()
        runCatching { context.startActivity(intent) }
    }

    AppSubPage(title = "Notifications", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                ListSection(
                    footer = if (prefs.notificationsEnabled) {
                        "Reminders alert you when they come due, a saved card that is about to expire gets a daily reminder, and " +
                            "you hear when an update is out or ready."
                    } else {
                        "Nothing alerts while this is off. Reminders still come due in Plans, card expiry still shows in Wallet, " +
                            "and updates show on Home when you open NormPix."
                    },
                ) {
                    item {
                        SettingsSwitchRow(
                            headline = "Allow notifications",
                            supporting = "Reminder alerts, card expiry reminders and updates",
                            icon = if (prefs.notificationsEnabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                            hue = SettingsPageNotificationsHue,
                            checked = prefs.notificationsEnabled,
                            onCheckedChange = { on ->
                                haptics.selection()
                                preferences.setNotificationsEnabled(on)
                                if (!on) AppNotifications.withdrawAll(context)
                                snackbars.show(if (on) "Notifications on" else "Notifications off")
                            },
                        )
                    }
                }
                if (prefs.notificationsEnabled && !androidAllows) {
                    ListSection(
                        title = "Blocked by Android",
                        footer = "Android isn't letting NormPix show notifications, so nothing alerts even with the switch above on.",
                    ) {
                        item {
                            ListRow(
                                headline = "Open Android notification settings",
                                supporting = "Allow notifications for NormPix there",
                                leading = { LeadingIcon(Icons.Outlined.Settings) },
                                onClick = ::openAndroidSettings,
                            )
                        }
                    }
                } else {
                    ListSection(footer = "Sound, vibration and which kinds alert are set per kind in Android's settings.") {
                        item {
                            ListRow(
                                headline = "Android notification settings",
                                leading = { LeadingIcon(Icons.Outlined.Settings) },
                                onClick = ::openAndroidSettings,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The colour of the Notifications group (see [hue]). */
internal const val SettingsPageNotificationsHue = 5
