package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.normpix.availability.ScheduledFeature
import com.example.normpix.availability.summary
import com.example.normpix.finance.ui.FinanceState
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.AccountButtonTarget
import com.example.normpix.settings.AppLockScope
import com.example.normpix.settings.AppPrefsState
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.AppTheme
import kotlinx.coroutines.launch

val SettingsGroups = SettingsPage.entries

val SettingsPage.title: String
    get() = when (this) {
        SettingsPage.Appearance -> "Appearance"
        SettingsPage.Finance -> "Finance"
        SettingsPage.Data -> "Data & Backup"
        SettingsPage.Wallet -> "Wallet & Cards"
        SettingsPage.Availability -> "Availability & Schedules"
        SettingsPage.Security -> "Security & Lock"
    }

val SettingsPage.icon: ImageVector
    get() = SettingsCatalog.iconFor(this)

/**
 * Settings: searchable, grouped into a few categories, each summarizing its
 * current state so most people never need to open them.
 */
@Composable
fun SettingsScreen() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val finance = rememberFinanceState()
    val activator = rememberSettingsActivator()
    val system = rememberSettingsSystemActions()
    val scope = rememberCoroutineScope()
    val is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = SettingsCatalog.search(query)

    AppSubPage(title = "Settings", onBack = navigator::back, imeAware = true) {
        item(key = "search") {
            Column(Modifier.padding(AppSpacing.page)) {
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Search settings")
                Spacer(Modifier.height(AppSpacing.sm))
            }
        }
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                when {
                    searching && results.isEmpty() -> AppEmptyState(
                        icon = Icons.Rounded.SearchOff,
                        title = "No settings match \"${query.trim()}\"",
                        message = "Try a different word, like \"dark\", \"backup\" or \"lock\".",
                    )
                    searching -> ListSection(title = "${results.size} ${if (results.size == 1) "result" else "results"}") {
                        for (entry in results) {
                            item {
                                SettingsRow(
                                    headline = entry.title,
                                    supporting = entry.location,
                                    icon = entry.icon,
                                    hue = null,
                                    onClick = { activator.activate(entry) },
                                )
                            }
                        }
                    }
                    else -> {
                        ListSection {
                            item {
                                ProfileSettingsRow(
                                    name = prefs.profileName,
                                    emoji = prefs.profileEmoji,
                                    photo = prefs.profilePhoto,
                                    accountButtonLeadsToProfile = prefs.accountButtonTarget == AccountButtonTarget.Profile,
                                    onClick = navigator::openProfile,
                                )
                            }
                        }
                        Spacer(Modifier.height(AppSpacing.lg))
                        ListSection {
                            for (group in SettingsGroups) {
                                item {
                                    SettingsRow(
                                        headline = group.title,
                                        supporting = summary(group, prefs, finance, is24Hour),
                                        icon = group.icon,
                                        hue = group.hue,
                                        onClick = { navigator.openSettings(group) },
                                    )
                                }
                            }
                        }
                        ListSection(title = "Sample Data") {
                            item {
                                SettingsSwitchRow(
                                    checked = prefs.dummyDataEnabled,
                                    onCheckedChange = { enabled ->
                                        scope.launch { container.preferences.setDummyDataEnabled(enabled) }
                                    },
                                    headline = "Sample data",
                                    supporting = "Explore NormPix with realistic sample accounts and transactions without affecting your personal data.",
                                    icon = Icons.Outlined.Science,
                                    hue = 6,
                                )
                            }
                        }
                        ListSection(title = "System") {
                            item {
                                SettingsRow(
                                    headline = "Reset settings",
                                    supporting = "Back to defaults. Your data isn't touched.",
                                    icon = Icons.Outlined.SettingsBackupRestore,
                                    hue = 5,
                                    onClick = { system.resetSettings() },
                                )
                            }
                            item {
                                SettingsRow(
                                    headline = "About NormPix",
                                    icon = Icons.Outlined.Info,
                                    hue = null,
                                    onClick = { system.showAbout() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun summary(group: SettingsPage, prefs: AppPrefsState, finance: FinanceState, is24Hour: Boolean): String = when (group) {
    SettingsPage.Appearance -> listOf(
        when (prefs.mode) {
            ThemeMode.System -> "System theme"
            ThemeMode.Light -> "Light"
            ThemeMode.Dark -> "Dark"
        },
        if (prefs.colorSource == ColorSource.System && AppTheme.dynamicColorSupported) "Wallpaper colors" else prefs.accent.label,
    ).joinToString(" · ")
    SettingsPage.Finance -> buildList {
        add(finance.currency.code)
        finance.defaultAccount?.let { add("Default: ${it.name}") }
        add("Categories")
    }.joinToString(" · ")
    SettingsPage.Data -> "Export, restore, erase"
    SettingsPage.Security ->
        if (prefs.walletLockEnabled) {
            "Locked · ${if (prefs.appLockScope == AppLockScope.EntireApp) "Entire app" else "Wallet only"}"
        } else {
            "Biometric lock off"
        }
    SettingsPage.Wallet -> prefs.walletCardActionStyle.label
    SettingsPage.Availability -> ScheduledFeature.entries.joinToString(" · ") { feature ->
        "${feature.shortLabel}: ${prefs.availabilityOf(feature).summary(is24Hour)}"
    }
}
