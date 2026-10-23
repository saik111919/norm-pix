package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.example.normpix.updates.UpdateState
import com.example.normpix.updates.ui.updateSummary
import kotlinx.coroutines.launch

/** The space under the search field, in the same list item: not part of the field, so not counted when asking if it is in view. */
private val SEARCH_GAP = AppSpacing.sm

/** The groups listed on the main Settings screen. Automatic updates is not one: it is reached from the Updates page, under System. */
val SettingsGroups = SettingsPage.entries - SettingsPage.Updates

/** The list item holding the search field, found by this key to tell whether the field is on screen. */
private const val SEARCH_ITEM_KEY = "search"

val SettingsPage.title: String
    get() = when (this) {
        SettingsPage.Appearance -> "Appearance"
        SettingsPage.Finance -> "Finance"
        SettingsPage.Data -> "Data & Backup"
        SettingsPage.Wallet -> "Wallet & Cards"
        SettingsPage.Availability -> "Availability & Schedules"
        SettingsPage.Notifications -> "Notifications"
        SettingsPage.Security -> "Security & Lock"
        SettingsPage.Updates -> "Automatic updates"
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
    val is24Hour = android.text.format.DateFormat.is24HourFormat(androidx.compose.ui.platform.LocalContext.current)
    var query by rememberSaveable { mutableStateOf("") }
    val searching = query.isNotBlank()
    val results = SettingsCatalog.search(query, cardsEnabled = prefs.walletCardsEnabled)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val searchFocus = remember { FocusRequester() }
    // Once the search field has scrolled away, it waits in the header as an icon: tapping it brings the field back and
    // puts the cursor in it. "Away" is the field itself being out of sight - wholly above the list's content, under the
    // header's controls or off the top - never merely the first item having moved: the page's own title is an item above the
    // field, and the icon used to appear the moment that left, with the field still there under it.
    val gapBelowField = with(LocalDensity.current) { SEARCH_GAP.roundToPx() }
    val searchOutOfView by remember(listState, gapBelowField) {
        derivedStateOf {
            val layout = listState.layoutInfo
            // Not measured yet: nothing has scrolled anywhere.
            if (layout.visibleItemsInfo.isEmpty()) return@derivedStateOf false
            val field = layout.visibleItemsInfo.firstOrNull { it.key == SEARCH_ITEM_KEY } ?: return@derivedStateOf true
            field.offset + field.size - gapBelowField <= 0
        }
    }

    val groupStyle = settingsGroupStyle()

    AppSubPage(
        title = "Settings",
        onBack = navigator::back,
        displayTitle = true,
        imeAware = true,
        listState = listState,
        // The header's pill holds nothing but this icon, so it appears with it: not before the field has scrolled away.
        actionsVisible = searchOutOfView,
        actions = {
            IconButton(
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                        runCatching { searchFocus.requestFocus() }
                    }
                },
            ) { Icon(Icons.Rounded.Search, contentDescription = "Search settings") }
        },
    ) {
        item(key = SEARCH_ITEM_KEY) {
            Column(Modifier.padding(AppSpacing.page)) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search settings",
                    modifier = Modifier.focusRequester(searchFocus),
                )
                Spacer(Modifier.height(SEARCH_GAP))
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
                    searching -> ListSection(title = "${results.size} ${if (results.size == 1) "result" else "results"}", style = groupStyle) {
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
                        ListSection(style = groupStyle) {
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
                        Spacer(Modifier.height(AppSpacing.md))
                        ListSection(style = groupStyle) {
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
                        ListSection(title = "System", style = groupStyle) {
                            item {
                                SettingsRow(
                                    headline = "Reset settings",
                                    supporting = "Back to defaults. Your data isn't touched.",
                                    icon = Icons.Outlined.SettingsBackupRestore,
                                    hue = 5,
                                    onClick = { system.resetSettings() },
                                )
                            }
                            // One row for everything about updating: it opens the update page, which holds the check, what
                            // is new, the changelog and the way to Automatic updates.
                            container.updates?.let { updater ->
                                item {
                                    val update by updater.state.collectAsState()
                                    SettingsRow(
                                        headline = UPDATES_ROW_TITLE,
                                        supporting = updateSummary(update, updater),
                                        icon = Icons.Outlined.SystemUpdate,
                                        hue = SettingsPageUpdatesHue,
                                        alert = update is UpdateState.Available || update is UpdateState.Ready,
                                        onClick = { system.checkForUpdates() },
                                        trailing = updateBadgeLabel(update)?.let { label -> { UpdateBadge(label) } },
                                    )
                                }
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

/** What the Updates row is called: the check, what is new and the changelog are all behind it. */
internal const val UPDATES_ROW_TITLE = "Updates & changelog"

/** What the small pill at the end of the Updates row says when there is something to do: Update, or Install. Null when there isn't. */
internal fun updateBadgeLabel(state: UpdateState): String? = when (state) {
    is UpdateState.Available -> "Update"
    is UpdateState.Ready -> "Install"
    else -> null
}

/** A small filled pill at the end of a row: the one thing waiting for the user, seen from the list. */
@Composable
private fun UpdateBadge(label: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.primary, contentColor = scheme.onPrimary) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs + 2.dp),
        )
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
    SettingsPage.Data -> if (prefs.dummyDataEnabled) "Sample data on · Export, restore, erase" else "Sample data, export, restore, erase"
    SettingsPage.Security ->
        if (prefs.walletLockEnabled) {
            "Locked · ${if (prefs.appLockScope == AppLockScope.EntireApp) "Entire app" else "Wallet only"}"
        } else {
            "Biometric lock off"
        }
    SettingsPage.Wallet -> if (prefs.walletCardsEnabled) prefs.walletCardActionStyle.label else "Cards aren't stored · Accounts only"
    SettingsPage.Notifications -> if (prefs.notificationsEnabled) "On · Reminders, card expiry and updates" else "Off"
    SettingsPage.Updates -> automaticUpdatesSummary(prefs)
    SettingsPage.Availability -> ScheduledFeature.entries.joinToString(" · ") { feature ->
        "${feature.shortLabel}: ${prefs.availabilityOf(feature).summary(is24Hour)}"
    }
}
