package com.example.normpix.notifications

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import com.example.normpix.navigation.NotificationStrip
import com.example.normpix.settings.AppPreferences
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerBar
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.theme.StatusTone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The notification centre: what is unread and what seeing it does, how that is kept, the bell that counts it, the page that lists it
 * and the strip above the "+" menu. The sources themselves (overdue reminders, budgets, card expiry, backup, update) are Home's
 * own banners and are tested where they are made.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NotificationsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "notifications_test")

    @AfterTest
    fun tearDown() = env.close()

    private fun note(
        kind: NotificationKind,
        key: String,
        title: String,
        tone: StatusTone = StatusTone.Error,
        message: String = "About $title",
        figure: BannerFigure? = null,
        bar: BannerBar? = null,
        onPrimary: () -> Unit = {},
        onSecondary: (() -> Unit)? = null,
        onOpen: () -> Unit = {},
    ) = AppNotification(
        kind,
        Banner(
            key = key,
            tone = tone,
            icon = Icons.Outlined.Notifications,
            eyebrow = "Overdue",
            title = title,
            message = message,
            figure = figure,
            bar = bar,
            primary = StatusAction("Open $title", onClick = onPrimary),
            secondary = onSecondary?.let { StatusAction("Later $title", onClick = it) },
            onOpen = onOpen,
        ),
    )

    private fun notifications(vararg items: AppNotification, seen: Set<String> = emptySet(), onSeen: (Set<String>) -> Unit = {}) =
        Notifications(items.toList(), seen, onSeen)

    @Test
    fun unreadIsWhatHasNotBeenSeen_andSeeingForgetsWhatHasGone() {
        val a = note(NotificationKind.Reminders, "a", "Pay rent")
        val b = note(NotificationKind.Budgets, "b", "Food budget")
        assertEquals(listOf("b"), notifications(a, b, seen = setOf("a", "gone")).unread.map { it.key })

        var saw: Set<String>? = null
        notifications(a, b, seen = setOf("a", "gone")) { saw = it }.markAllSeen()
        // What is listed now, and nothing else: "gone" is forgotten, so it is new again if it comes back.
        assertEquals(setOf("a", "b"), saw)
        assertNull(notifications().unread.firstOrNull())
    }

    @Test
    fun whatWasSeenIsKeptOnThisPhone_andNeverBackedUp() {
        env.preferences.markNotificationsSeen(setOf("reminder-1", "budget-overall"))
        assertEquals(setOf("reminder-1", "budget-overall"), AppPreferences(env.prefsStore).state.value.notificationsSeen, "read back by the next launch")
        assertFalse("notifications_seen" in env.preferences.exportAll().keys, "ids differ from phone to phone: it is not backed up")
        env.preferences.resetToDefaults()
        assertEquals(setOf("reminder-1", "budget-overall"), env.preferences.state.value.notificationsSeen, "resetting settings does not make everything new")
    }

    @Test
    fun theBellCountsWhatIsNew_andOpensThePage() {
        val items = arrayOf(note(NotificationKind.Reminders, "a", "Pay rent"), note(NotificationKind.Budgets, "b", "Food budget"))
        compose.setContent { env.Content { CompositionLocalProvider(LocalNotifications provides notifications(*items)) { NotificationBell() } } }
        compose.onNodeWithContentDescription("Notifications, 2 new").performClick()
        assertEquals(1, env.navigator.notificationsOpened)
    }

    @Test
    fun theBellIsPlain_whenNothingIsNew() {
        val items = arrayOf(note(NotificationKind.Reminders, "a", "Pay rent"))
        compose.setContent {
            env.Content { CompositionLocalProvider(LocalNotifications provides notifications(*items, seen = setOf("a"))) { NotificationBell() } }
        }
        compose.onNodeWithContentDescription("Notifications").assertIsDisplayed()
    }

    @Test
    fun thePageListsThemByKind_andSeeingItSaysTheyWereSeen() {
        var saw: Set<String>? = null
        val items = arrayOf(
            note(NotificationKind.Reminders, "r1", "Pay rent"),
            note(NotificationKind.Budgets, "b1", "Food budget"),
            note(NotificationKind.Cards, "c1", "HDFC card"),
        )
        compose.setContent {
            env.Content { CompositionLocalProvider(LocalNotifications provides notifications(*items) { saw = it }) { NotificationsPage() } }
        }
        compose.waitForIdle()
        // A titled group for each kind, as every list on the other pages is, and a banner in it for each notification.
        for (heading in listOf("Reminders", "Budgets", "Cards")) compose.onAllNodesWithText(heading).assertCountEquals(1)
        for (title in listOf("Pay rent", "Food budget", "HDFC card")) {
            // Banners are tall: the page is a list, so each is scrolled to, as it is composed then.
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasContentDescription(spokenOf(title)))
            // One sentence to a screen reader, as Home's banner is - its panels are drawn, not read one by one.
            compose.onNodeWithContentDescription(spokenOf(title)).assertIsDisplayed()
            // What it is stays a word on each banner, not only a colour.
            compose.onNode(hasText("Overdue") and hasAnyAncestor(hasContentDescription(spokenOf(title))), useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("About $title", useUnmergedTree = true).assertIsDisplayed()
            // Its button is on the bar under the panels.
            compose.onNodeWithText("Open $title", useUnmergedTree = true).assertIsDisplayed()
        }
        assertEquals(setOf("r1", "b1", "c1"), saw, "opening the page is seeing it all")
        // The way to Settings is at the foot of the page, past the notifications: scrolled to, as it is composed then.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Notification settings"))
        compose.onNodeWithText("Notification settings").assertIsDisplayed()
    }

    @Test
    fun aCrashReportAndAnUpdateShareOneHeading() {
        val items = arrayOf(note(NotificationKind.App, "crash", "Stopped working"), note(NotificationKind.Update, "update", "NormPix 1.5.10"))
        compose.setContent { env.Content { CompositionLocalProvider(LocalNotifications provides notifications(*items)) { NotificationsPage() } } }
        compose.waitForIdle()
        compose.onAllNodesWithText("NormPix").assertCountEquals(1)
        compose.onNodeWithContentDescription(spokenOf("Stopped working")).assertIsDisplayed()
        compose.onNodeWithContentDescription(spokenOf("NormPix 1.5.10")).assertIsDisplayed()
    }

    /** What a screen reader says of [note]'s banner (its eyebrow, title and message). */
    private fun spokenOf(title: String) = "Overdue. $title. About $title"

    @Test
    fun aBannerOpensWhatItIsAbout_andItsButtonsDoTheirOwnThings() {
        val calls = mutableListOf<String>()
        val item = note(
            NotificationKind.Reminders, "r1", "Pay rent",
            onPrimary = { calls += "primary" }, onSecondary = { calls += "secondary" }, onOpen = { calls += "open" },
        )
        compose.setContent { env.Content { CompositionLocalProvider(LocalNotifications provides notifications(item)) { NotificationsPage() } } }
        compose.waitForIdle()

        // The bar's buttons are pressed by touch: a screen reader has them as actions on the banner (below), so they are not nodes
        // of their own in the merged tree.
        compose.onNodeWithText("Later Pay rent", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Open Pay rent", useUnmergedTree = true).performClick()
        // The panels are the way in: pressing them is opening it, not pressing one of the buttons.
        compose.onNodeWithContentDescription(spokenOf("Pay rent")).performClick()
        assertEquals(listOf("secondary", "primary", "open"), calls)
    }

    @Test
    fun aBannerIsOneButtonToAScreenReader_withItsButtonsAsActionsOnIt() {
        val calls = mutableListOf<String>()
        val item = note(
            NotificationKind.Reminders, "r1", "Pay rent",
            onPrimary = { calls += "primary" }, onSecondary = { calls += "secondary" }, onOpen = { calls += "open" },
        )
        compose.setContent { env.Content { CompositionLocalProvider(LocalNotifications provides notifications(item)) { NotificationsPage() } } }
        compose.waitForIdle()

        val config = compose.onNodeWithContentDescription(spokenOf("Pay rent")).fetchSemanticsNode().config
        assertEquals(Role.Button, config[SemanticsProperties.Role])
        // The quieter button first, as on the bar.
        val actions = config[SemanticsActions.CustomActions]
        assertEquals(listOf("Later Pay rent", "Open Pay rent"), actions.map { it.label })
        actions.forEach { assertTrue(it.action()) }
        assertTrue(config[SemanticsActions.OnClick].action!!.invoke())
        assertEquals(listOf("secondary", "primary", "open"), calls)
        // Neither the panels' words nor the buttons' are a thing of their own to read: the banner is one sentence.
        compose.onAllNodesWithText("Open Pay rent").assertCountEquals(0)
        compose.onAllNodesWithText("Pay rent").assertCountEquals(0)
    }

    @Test
    fun theBannersPanelsSitSideBySide_andLargeTextStacksThem_asHomesDo() {
        var scale by mutableFloatStateOf(1f)
        val late = note(NotificationKind.Reminders, "r", "Pay rent", message = "Payments", figure = BannerFigure("Late by", "3 days", "Was due 6 Nov"))
        compose.setContent {
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, scale),
                    LocalNotifications provides notifications(late),
                ) { NotificationsPage() }
            }
        }
        compose.waitForIdle()
        fun panels() = compose.onNodeWithText("Pay rent", useUnmergedTree = true).getBoundsInRoot() to
            compose.onNodeWithText("Late by", useUnmergedTree = true).getBoundsInRoot()

        val (title, figure) = panels()
        assertTrue(figure.left >= title.right, "the figure is beside what it is about: $figure beside $title")
        assertTrue(figure.top < title.bottom, "level with it, not under it: $figure, $title")

        compose.runOnIdle { scale = 1.6f }
        compose.waitForIdle()
        val (largeTitle, largeFigure) = panels()
        assertTrue(largeFigure.top >= largeTitle.bottom, "large text puts the figure's panel under the other: $largeFigure under $largeTitle")
    }

    @Test
    fun theSummarySaysWhatIsWaiting_kindByKind() {
        fun of(vararg kinds: NotificationKind) = attentionSummary(kinds.mapIndexed { i, kind -> note(kind, "k$i", "T$i") })
        assertEquals("", attentionSummary(emptyList()))
        assertEquals("1 reminder", of(NotificationKind.Reminders))
        assertEquals("2 reminders", of(NotificationKind.Reminders, NotificationKind.Reminders))
        assertEquals("2 reminders and 1 budget", of(NotificationKind.Budgets, NotificationKind.Reminders, NotificationKind.Reminders))
        assertEquals(
            "1 reminder, 1 budget and 1 card",
            of(NotificationKind.Cards, NotificationKind.Budgets, NotificationKind.Reminders),
        )
        // A fourth kind is not a line any more: the rest are counted, not named.
        assertEquals(
            "1 reminder, 1 budget, 1 card and 2 more",
            of(NotificationKind.Reminders, NotificationKind.Budgets, NotificationKind.Cards, NotificationKind.Backup, NotificationKind.App),
        )
    }

    @Test
    fun homesOneBannerCountsThemAll_inTheColourOfTheWorst_andOpensThePage() {
        var opened = 0
        val warning = note(NotificationKind.Backup, "b", "Back up", tone = StatusTone.Warning)
        val info = note(NotificationKind.Cards, "c", "Card", tone = StatusTone.Info)
        val error = note(NotificationKind.Reminders, "r", "Rent", tone = StatusTone.Error)

        val one = attentionBanner(listOf(warning), open = { opened++ })
        assertEquals("1 thing needs attention", one.title)
        assertEquals(StatusTone.Warning, one.tone)
        assertNull(one.figure, "one full-width panel: the count is the title, and the width is for what it is made of")

        val several = attentionBanner(listOf(info, warning, error), open = { opened++ })
        assertEquals("3 things need attention", several.title)
        assertEquals(StatusTone.Error, several.tone, "the worst of them decides the colour")
        assertEquals("1 reminder, 1 card and 1 backup due", several.message, "kind by kind, in the page's order")
        assertEquals(StatusTone.Info, attentionBanner(listOf(info), open = {}).tone)

        // Pressing the banner and its button both go to the page.
        several.onOpen()
        several.primary.onClick()
        assertEquals(2, opened)
    }

    @Test
    fun aBannerDrawsItsFigureInItsOwnPanel_andSaysItToo() {
        val late = note(NotificationKind.Reminders, "r", "Rent", message = "Payments", figure = BannerFigure("Late by", "3 days", "Was due 6 Nov"))
        compose.setContent {
            env.Content { CompositionLocalProvider(LocalNotifications provides notifications(late)) { NotificationsPage() } }
        }
        compose.waitForIdle()
        // A panel beside what it is about: the figure's title, the figure and its note, as Home's banner draws them - and read too.
        compose.onNodeWithText("Late by", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("3 days", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Was due 6 Nov", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Overdue. Rent. Payments. Late by 3 days, Was due 6 Nov").assertIsDisplayed()
    }

    @Test
    fun aBannerWithOneButton_hasItOnItsBar_whetherOrNotItDoesWhatPressingTheBannerDoes() {
        var opened = 0
        val open: () -> Unit = { opened++ }
        val budget = Banner(
            key = "budget-food",
            tone = StatusTone.Warning,
            icon = Icons.Outlined.Notifications,
            eyebrow = "Nearly used",
            title = "Food budget",
            message = "Rs 8,000 for November",
            primary = StatusAction("View budgets", onClick = open),
            bar = BannerBar(0.85f, "85% used"),
            onOpen = open,
        )
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalNotifications provides notifications(AppNotification(NotificationKind.Budgets, budget))) { NotificationsPage() }
            }
        }
        compose.waitForIdle()
        // As on Home, where every banner has its bar: the button is there, and so is the bar - a ring beside the lines (since
        // 2026-11-15) with how far along it is inside; the words a screen reader hears ("85% used") stay the bar's label.
        compose.onNodeWithText("View budgets", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("85%", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("85% used", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("View budgets", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Nearly used. Food budget. Rs 8,000 for November. 85% used").performClick()
        assertEquals(2, opened)
    }

    @Test
    fun thePageSaysSoWhenThereIsNothing() {
        compose.setContent { env.Content { CompositionLocalProvider(LocalNotifications provides notifications()) { NotificationsPage() } } }
        compose.onNodeWithText("You're all caught up").assertIsDisplayed()
    }

    @Test
    fun theMenusStripShowsThreeNewOnes_andTheWayToTheRest() {
        var opened: String? = null
        var seeAll = 0
        val five = (1..5).map { note(NotificationKind.Reminders, "r$it", "Task $it") }.toTypedArray()
        compose.setContent {
            env.Content {
                NotificationStrip(notifications(*five), progress = { 1f }, onOpen = { opened = it.key }, onSeeAll = { seeAll++ })
            }
        }
        for (shown in 1..3) compose.onNodeWithContentDescription("Overdue. Task $shown. About Task $shown").assertIsDisplayed()
        compose.onNodeWithContentDescription("Overdue. Task 4. About Task 4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Overdue. Task 2. About Task 2").performClick()
        assertEquals("r2", opened)
        compose.onNodeWithText("All notifications (2 more)").performClick()
        assertEquals(1, seeAll)
    }

    @Test
    fun theMenusStripIsNothing_whenNothingIsNew() {
        val one = note(NotificationKind.Reminders, "a", "Pay rent")
        compose.setContent {
            env.Content {
                NotificationStrip(notifications(one, seen = setOf("a")), progress = { 1f }, onOpen = {}, onSeeAll = {})
            }
        }
        compose.onNodeWithContentDescription("Overdue. Pay rent. About Pay rent").assertDoesNotExist()
        compose.onAllNodesWithText("All notifications").assertCountEquals(0)
    }
}
