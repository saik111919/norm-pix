package com.example.normpix.notifications

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.normpix.navigation.NotificationStrip
import com.example.normpix.settings.AppPreferences
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.theme.StatusTone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
        // A titled group for each kind, as every list on the other pages is, and a row in it for each notification.
        for (heading in listOf("Reminders", "Budgets", "Cards")) compose.onAllNodesWithText(heading).assertCountEquals(1)
        for (title in listOf("Pay rent", "Food budget", "HDFC card")) {
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText("About $title").assertIsDisplayed()
            compose.onNodeWithText("Open $title").assertIsDisplayed()
        }
        // What it is stays a word on each row, not only a colour.
        compose.onAllNodesWithText("Overdue").assertCountEquals(3)
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
        compose.onNodeWithText("Stopped working").assertIsDisplayed()
        compose.onNodeWithText("NormPix 1.5.10").assertIsDisplayed()
    }

    @Test
    fun aRowOpensWhatItIsAbout_andItsButtonsDoTheirOwnThings() {
        val calls = mutableListOf<String>()
        val item = note(
            NotificationKind.Reminders, "r1", "Pay rent",
            onPrimary = { calls += "primary" }, onSecondary = { calls += "secondary" }, onOpen = { calls += "open" },
        )
        compose.setContent { env.Content { CompositionLocalProvider(LocalNotifications provides notifications(item)) { NotificationsPage() } } }
        compose.waitForIdle()

        compose.onNodeWithText("Later Pay rent").performClick()
        compose.onNodeWithText("Open Pay rent").performClick()
        // The row itself is the way in: pressing its text is opening it, not pressing one of the buttons.
        compose.onNodeWithText("Pay rent").performClick()
        assertEquals(listOf("secondary", "primary", "open"), calls)
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
    fun aRowShowsItsFigureAtItsEnd_unlessTheWordsAlreadySayIt() {
        val late = note(NotificationKind.Reminders, "r", "Rent", message = "Payments", figure = BannerFigure("Late by", "3 days")).banner
        assertEquals("3 days", late.rowFigure()?.figure)
        val backup = note(NotificationKind.Backup, "b", "Back up", message = "Last backup 30 days ago", figure = BannerFigure("Last backup", "30 days")).banner
        assertNull(backup.rowFigure(), "the figure is in the sentence already")
        assertNull(note(NotificationKind.Reminders, "r", "Rent").banner.rowFigure())

        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalNotifications provides notifications(AppNotification(NotificationKind.Reminders, late))) { NotificationsPage() }
            }
        }
        compose.waitForIdle()
        // Beside the lines, not under them: what it is, and the figure.
        compose.onNodeWithText("Late by").assertIsDisplayed()
        compose.onNodeWithText("3 days").assertIsDisplayed()
    }

    @Test
    fun aRowWhoseOnlyButtonOpensIt_hasNoButton_theRowIsTheButton() {
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
            onOpen = open,
        )
        assertEquals(emptyList(), budget.rowActions())
        // With a second button, or a main one that does something else, both are there, the quieter one first.
        val reminder = note(NotificationKind.Reminders, "r", "Rent", onSecondary = {}).banner
        assertEquals(listOf("Later Rent", "Open Rent"), reminder.rowActions().map { it.label })

        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalNotifications provides notifications(AppNotification(NotificationKind.Budgets, budget))) { NotificationsPage() }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("View budgets").assertCountEquals(0)
        compose.onNodeWithText("Food budget").performClick()
        assertEquals(1, opened)
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
