package com.example.normpix.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.normpix.about.GuideTopic
import com.example.normpix.about.ui.AboutPage
import com.example.normpix.about.ui.GuidePage
import com.example.normpix.navigation.AppNavigator
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.navigation.FloatingBackRow
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.SettingsPage
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.ui.components.DetailPane
import com.example.normpix.ui.components.LocalDetailPane
import com.example.normpix.ui.components.LocalDockHosted
import com.example.normpix.ui.components.LocalFrostedBackdrop
import com.example.normpix.ui.components.frostedSource
import com.example.normpix.ui.components.rememberBackButtonStyle
import com.example.normpix.ui.components.rememberFrostedBackdrop
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.rememberListDetail
import com.example.normpix.updates.ui.ChangelogPage
import com.example.normpix.updates.ui.UpdatePage

/**
 * What the right-hand side of Settings shows when it has the room for one: a page that opens from the list. The same pages a
 * phone opens one at a time, one over the other.
 */
sealed interface SettingsDetail {
    /** The page this one is a step inside of, which Back returns to; null for a page the list itself opens. */
    val parent: SettingsDetail?

    /** What a route or a saved state calls it. */
    val key: String

    /** The Profile page. */
    data object Profile : SettingsDetail {
        override val parent: SettingsDetail? = null
        override val key = "profile"
    }

    /** A group of settings (Appearance, Finance...), or Automatic updates, which is a step inside the update page. */
    data class Page(val page: SettingsPage) : SettingsDetail {
        override val parent: SettingsDetail? get() = if (page == SettingsPage.Updates) Update else null
        override val key get() = "page:${page.name}"
    }

    /** One screen of Appearance (its colors, its font...), a step inside Appearance. */
    data class Appearance(val section: AppearanceSection) : SettingsDetail {
        override val parent: SettingsDetail get() = Page(SettingsPage.Appearance)
        override val key get() = "appearance:${section.name}"
    }

    /** The update page: the check, what is new, the changelog. */
    data object Update : SettingsDetail {
        override val parent: SettingsDetail? = null
        override val key = "update"
    }

    /** The changelog page: what changed across NormPix releases. */
    data object Changelog : SettingsDetail {
        override val parent: SettingsDetail get() = Update
        override val key = "changelog"
    }

    /** The About page: what NormPix is, and the guide to how each of its features works. */
    data object About : SettingsDetail {
        override val parent: SettingsDetail? = null
        override val key = "about"
    }

    /** One topic of the guide on the About page, a step inside About. */
    data class Guide(val topic: GuideTopic) : SettingsDetail {
        override val parent: SettingsDetail get() = About
        override val key get() = "guide:${topic.name}"
    }

    companion object {
        /** The detail a [key] stands for, or null for one that is none (a stale saved state). */
        fun fromKey(key: String): SettingsDetail? = when {
            key == Profile.key -> Profile
            key == Update.key -> Update
            key == Changelog.key -> Changelog
            key == About.key -> About
            key.startsWith("guide:") -> GuideTopic.named(key.removePrefix("guide:"))?.let(::Guide)
            key.startsWith("page:") -> SettingsPage.entries.firstOrNull { it.name == key.removePrefix("page:") }?.let(::Page)
            key.startsWith("appearance:") -> AppearanceSection.named(key.removePrefix("appearance:"))?.let(::Appearance)
            else -> null
        }
    }
}

/** What is open beside the list until something is chosen: the first group, as a tablet's own Settings opens on its first. */
val DefaultSettingsDetail: SettingsDetail = SettingsDetail.Page(SettingsGroups.first())

/** The group of the list this detail belongs to (an Appearance screen is Appearance's), or null if it is none of them. */
internal val SettingsDetail.group: SettingsPage?
    get() = when (this) {
        is SettingsDetail.Page -> page
        is SettingsDetail.Appearance -> SettingsPage.Appearance
        else -> null
    }

/** Whether this detail is the one the "Updates & changelog" row stands for: the update page, the changelog, and Automatic updates under it. */
internal val SettingsDetail.isUpdates: Boolean
    get() = this == SettingsDetail.Update || this == SettingsDetail.Changelog || this == SettingsDetail.Page(SettingsPage.Updates)

/** Whether this detail is the one the "About NormPix" row stands for: the About page, and a topic of its guide. */
internal val SettingsDetail.isAbout: Boolean
    get() = this == SettingsDetail.About || this is SettingsDetail.Guide

private val SettingsDetailSaver = Saver<SettingsDetail, String>(save = { it.key }, restore = { SettingsDetail.fromKey(it) })

/** The page of a settings group - the one screen every group has, whether a phone opens it over the list or a tablet beside it. */
@Composable
fun SettingsPageContent(page: SettingsPage) {
    when (page) {
        SettingsPage.Appearance -> AppearanceSettingsPage()
        SettingsPage.Widgets -> WidgetsSettingsPage()
        SettingsPage.Finance -> FinanceSettingsPage()
        SettingsPage.Banks -> BanksSettingsPage()
        SettingsPage.Data -> DataSettingsPage()
        SettingsPage.Security -> SecuritySettingsPage()
        SettingsPage.Wallet -> WalletSettingsPage()
        SettingsPage.Availability -> AvailabilitySettingsPage()
        SettingsPage.Notifications -> NotificationsSettingsPage()
        SettingsPage.Updates -> UpdatesSettingsPage()
    }
}

/**
 * Where Settings, its pages, the Profile and the update page lead: [opens] is the page asked for (null: Settings itself).
 * A window with room for it (see [rememberListDetail]) shows the list with that page beside it, [SettingsTwoPane]; a phone shows
 * the page alone, over what it was opened from.
 */
@Composable
fun SettingsRoute(opens: SettingsDetail? = null) {
    if (rememberListDetail()) {
        SettingsTwoPane(opens)
        return
    }
    when (opens) {
        null -> SettingsScreen()
        SettingsDetail.Profile -> ProfilePage()
        is SettingsDetail.Page -> SettingsPageContent(opens.page)
        is SettingsDetail.Appearance -> AppearanceSectionPage(opens.section)
        SettingsDetail.Update -> UpdatePage()
        SettingsDetail.Changelog -> ChangelogPage()
        SettingsDetail.About -> AboutPage()
        is SettingsDetail.Guide -> GuidePage(opens.topic)
    }
}

/**
 * Settings on a tablet, as a tablet's own Settings is: the list on the left, always there, and the page it opens on the right,
 * with the row of that page filled. Choosing another row swaps the page on the right; nothing slides over anything, and Back
 * leaves Settings (or, inside one of Appearance's screens, goes up to Appearance).
 *
 * The pages are the ones a phone shows full screen. They ask [LocalNavigator] to open one another, so each side is given a
 * navigator ([PaneNavigator]) that answers for what belongs in Settings - open Appearance, open the Profile - by changing the
 * page beside the list, and leaves the rest (the categories, the people) to the app's own.
 */
@Composable
fun SettingsTwoPane(opens: SettingsDetail? = null) {
    val outer = LocalNavigator.current
    var detail by rememberSaveable(stateSaver = SettingsDetailSaver) { mutableStateOf(opens ?: DefaultSettingsDetail) }
    val show = { next: SettingsDetail -> detail = next }
    val up = { detail = detail.parent ?: DefaultSettingsDetail }
    val listNavigator = remember(outer) { PaneNavigator(outer, show, leave = outer::back) }
    val pageNavigator = remember(outer) { PaneNavigator(outer, show, leave = up) }

    // Back inside a step (an Appearance screen) goes up to its page before it leaves Settings.
    BackHandler(enabled = detail.parent != null) { up() }

    // In the bottom-dock style the dock is one pill across the foot of the window, not of the list: the panes keep their room for it
    // (see LocalDockHosted) and it is drawn here, over both, as glass over what they show.
    val dock = rememberBackButtonStyle() == BackButtonStyle.BottomDock
    val backdrop = rememberFrostedBackdrop()
    val panel = MaterialTheme.colorScheme.surfaceContainerLow
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalDockHosted provides true) {
            Row(Modifier.fillMaxSize().frostedSource(backdrop)) {
                CompositionLocalProvider(LocalNavigator provides listNavigator) {
                    Box(Modifier.width(AppLayout.listPaneWidth).fillMaxHeight()) { SettingsListPane(selected = detail) }
                }
                // The page is a panel of its own tone, set off from the list by that and not by a line.
                Box(Modifier.weight(1f).fillMaxHeight().background(panel)) {
                    CompositionLocalProvider(LocalNavigator provides pageNavigator) {
                        Crossfade(
                            targetState = detail,
                            animationSpec = tween(AppMotion.duration(AppMotion.SHORT_MS)),
                            label = "settings_detail",
                        ) { shown ->
                            CompositionLocalProvider(LocalDetailPane provides DetailPane(canGoBack = shown.parent != null, container = panel)) {
                                SettingsDetailPage(shown)
                            }
                        }
                    }
                }
            }
        }
        if (dock) {
            CompositionLocalProvider(LocalFrostedBackdrop provides backdrop) {
                FloatingBackRow(
                    title = "Settings",
                    onBack = outer::back,
                    onCompanionClick = { outer.openSearch() },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun SettingsDetailPage(detail: SettingsDetail) {
    when (detail) {
        SettingsDetail.Profile -> ProfilePage()
        is SettingsDetail.Page -> SettingsPageContent(detail.page)
        is SettingsDetail.Appearance -> AppearanceSectionPage(detail.section)
        SettingsDetail.Update -> UpdatePage()
        SettingsDetail.Changelog -> ChangelogPage()
        SettingsDetail.About -> AboutPage()
        is SettingsDetail.Guide -> GuidePage(detail.topic)
    }
}

/**
 * The app's navigator, with what belongs in Settings answered by the two panes instead: opening a settings page, an Appearance
 * screen, the Profile or the update page shows it beside the list, and [leave] is what Back does (the list's leaves Settings;
 * a page's goes up a step). Opening Settings itself is already done - it is on screen. Everything else is the app's own.
 */
private class PaneNavigator(
    outer: AppNavigator,
    private val show: (SettingsDetail) -> Unit,
    private val leave: () -> Unit,
) : AppNavigator by outer {
    override fun back() = leave()

    override fun openSettings(page: SettingsPage?) {
        if (page != null) show(SettingsDetail.Page(page))
    }

    override fun openAppearance(section: AppearanceSection) = show(SettingsDetail.Appearance(section))

    override fun openProfile() = show(SettingsDetail.Profile)

    override fun openUpdates() = show(SettingsDetail.Update)

    override fun openChangelog() = show(SettingsDetail.Changelog)

    override fun openAbout() = show(SettingsDetail.About)

    override fun openGuide(topic: GuideTopic) = show(SettingsDetail.Guide(topic))
}
