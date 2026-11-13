package com.example.normpix.notifications

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.NotificationStrip
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerBar
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.BannerStack
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.StatusTone
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The notification centre pictured whole (a tall window, so nothing is scrolled out of the picture), to be looked at in
 * `app/build/qa_notifications/`: the page, the bell, and the strip the "+" menu shows. What the tests cannot see - a banner's
 * panel corners, the badge on the bell, the strip over a panel - is what these are for.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h1400dp-xhdpi")
class NotificationsRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "notifications_render")
    private val outDir = File("build/qa_notifications").also { it.mkdirs() }

    @AfterTest
    fun tearDown() = env.close()

    private fun dump(name: String) {
        val file = File(outDir, "$name.png")
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun reminder(): AppNotification = AppNotification(
        NotificationKind.Reminders,
        Banner(
            key = "reminder-8",
            tone = StatusTone.Error,
            icon = Icons.Outlined.Notifications,
            eyebrow = "Overdue",
            title = "Pay the electricity bill",
            message = "Bills",
            figure = BannerFigure("Late by", "2 days", "Was due 6 Nov"),
            primary = StatusAction("Mark paid", onClick = {}),
            secondary = StatusAction("Snooze", onClick = {}),
            onOpen = {},
        ),
    )

    private fun budget(): AppNotification = AppNotification(
        NotificationKind.Budgets,
        Banner(
            key = "budget-food",
            tone = StatusTone.Warning,
            icon = Icons.Outlined.PieChart,
            eyebrow = "Nearly used",
            title = "Food & dining budget",
            message = "Rs 8,000 for November",
            // As the real one: its button opens the budgets, which is what pressing it does too.
            primary = StatusAction("View budgets", onClick = open),
            bar = BannerBar(0.85f, "85% used"),
            figure = BannerFigure("Left", "Rs 1,200"),
            onOpen = open,
        ),
    )

    private val open: () -> Unit = {}

    private fun secondReminder(): AppNotification = AppNotification(
        NotificationKind.Reminders,
        Banner(
            key = "reminder-9",
            tone = StatusTone.Error,
            icon = Icons.Outlined.Notifications,
            eyebrow = "Overdue",
            title = "Pay society maintenance",
            message = "Payments",
            figure = BannerFigure("Late by", "1 day", "Was due 10 Nov"),
            primary = StatusAction("Mark as paid", onClick = {}),
            secondary = StatusAction("Snooze", onClick = {}),
            onOpen = {},
        ),
    )

    private fun update(): AppNotification = AppNotification(
        NotificationKind.Update,
        Banner(
            key = "update",
            tone = StatusTone.Info,
            icon = Icons.Outlined.Backup,
            eyebrow = "App update",
            title = "Downloading 1.5.10",
            message = "Checked before it installs",
            primary = StatusAction("Details", onClick = {}),
            secondary = StatusAction("Cancel", onClick = {}),
            bar = BannerBar(0.42f, "42%", "5 MB", "12 MB"),
            onOpen = {},
        ),
    )

    private fun card(): AppNotification = AppNotification(
        NotificationKind.Cards,
        Banner(
            key = "card-expiry",
            tone = StatusTone.Info,
            icon = Icons.Outlined.CreditCard,
            eyebrow = "Expires this month",
            title = "HDFC Millennia",
            message = "Ends in 4242",
            primary = StatusAction("Open card", onClick = {}),
            onOpen = {},
        ),
    )

    private fun backup(): AppNotification = AppNotification(
        NotificationKind.Backup,
        Banner(
            key = "backup-due",
            tone = StatusTone.Info,
            icon = Icons.Outlined.Backup,
            eyebrow = "Backup",
            title = "Back up your data",
            message = "Last backup 30 days ago",
            primary = StatusAction("Back up", onClick = {}),
            secondary = StatusAction("Later", onClick = {}),
            figure = BannerFigure("Last backup", "30 days", "Asks every 2 weeks"),
            onOpen = {},
        ),
    )

    /** Two credit-card bills as Home and the Wallet show them: a stack, the soonest due in front. */
    @Test
    fun theBillsStack() {
        val now = java.time.LocalDateTime.now().withNano(0)
        val today = java.time.LocalDate.now()
        kotlinx.coroutines.runBlocking {
            env.container.finance.load()
            for ((name, owed, due) in listOf(Triple("Axis Credit Card", 4_263_00L, today.minusDays(14)), Triple("ICICI Credit Card", 30_726_00L, today.plusDays(9)))) {
                env.container.finance.saveAccount(
                    com.example.normpix.finance.domain.Account(
                        name = name,
                        kind = com.example.normpix.finance.domain.AccountKind.Card,
                        openingBalance = -owed,
                        statementDay = due.minusDays(16).dayOfMonth,
                        dueDay = due.dayOfMonth,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) { com.example.normpix.screens.BillsDue() }
                }
            }
        }
        compose.waitUntil(15_000) {
            compose.waitForIdle()
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(" bill, ", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        dump("bills_stack_light")
        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        dump("bills_stack_dark")
    }

    /** Home's Needs attention as it is now: one banner for what is waiting, and the update's own under it. */
    @Test
    fun homesNeedsAttention() {
        val waiting = listOf(reminder(), budget(), card(), backup())
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        BannerStack(listOf(attentionBanner(waiting, open = {})), rememberDeckStackState())
                        BannerStack(listOf(update().banner), rememberDeckStackState())
                        // A stack of several: the panels are what is stacked, over one bar of buttons that stays where it is.
                        BannerStack(listOf(reminder().banner, secondReminder().banner), rememberDeckStackState())
                    }
                }
            }
        }
        compose.waitForIdle()
        dump("home_needs_attention_light")
        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        dump("home_needs_attention_dark")
    }

    @Test
    fun thePageWithFourKinds() {
        val items = listOf(reminder(), secondReminder(), budget(), card(), backup(), update())
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalNotifications provides Notifications(items, emptySet()) {}) { NotificationsPage() }
                }
            }
        }
        compose.waitForIdle()
        dump("notifications_page_light")
        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        dump("notifications_page_dark")
    }

    @Test
    fun theEmptyPage() {
        compose.setContent {
            env.Content {
                Surface(color = MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalNotifications provides Notifications.None) { NotificationsPage() }
                }
            }
        }
        compose.waitForIdle()
        dump("notifications_empty")
    }

    @Test
    fun theBellAndTheStrip() {
        val items = listOf(reminder(), budget(), card(), backup())
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalNotifications provides Notifications(items, emptySet()) {}) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.BottomCenter) {
                        androidx.compose.foundation.layout.Column(Modifier.width(360.dp).padding(16.dp)) {
                            NotificationStrip(
                                notifications = LocalNotifications.current,
                                progress = { 1f },
                                onOpen = {},
                                onSeeAll = {},
                            )
                            NotificationBell()
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        dump("notifications_strip")
    }
}
