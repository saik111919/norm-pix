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

    private fun note(kind: NotificationKind, key: String, title: String) = AppNotification(
        kind,
        Banner(
            key = key,
            tone = StatusTone.Error,
            icon = Icons.Outlined.Notifications,
            eyebrow = "Overdue",
            title = title,
            message = "About $title",
            primary = StatusAction("Open $title", onClick = {}),
            onOpen = {},
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
        for (heading in listOf("Reminders", "Budgets", "Cards")) compose.onAllNodesWithText(heading).assertCountEquals(1)
        // A banner is one thing to read, as on Home: its lines are spoken together, and its buttons are its actions.
        for (title in listOf("Pay rent", "Food budget", "HDFC card")) {
            compose.onNodeWithContentDescription("Overdue. $title. About $title").assertIsDisplayed()
        }
        assertEquals(setOf("r1", "b1", "c1"), saw, "opening the page is seeing it all")
        // The way to Settings is at the foot of the page, past the notifications: scrolled to, as it is composed then.
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Notification settings"))
        compose.onNodeWithText("Notification settings").assertIsDisplayed()
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
