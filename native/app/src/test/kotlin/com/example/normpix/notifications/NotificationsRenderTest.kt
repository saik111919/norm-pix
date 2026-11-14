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
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.NotificationStrip
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.Banner
import com.example.normpix.ui.components.BannerBar
import com.example.normpix.ui.components.BannerCard
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.BannerStack
import com.example.normpix.ui.components.StatusAction
import com.example.normpix.ui.components.rememberDeckStackState
import com.example.normpix.ui.theme.StatusTone
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
            bar = BannerBar(0.85f, "85% used", "Rs 0", "Rs 8,000"),
            figure = BannerFigure("Left", "Rs 1,200"),
            onOpen = open,
        ),
    )

    private val open: () -> Unit = {}

    private fun crash(): AppNotification = AppNotification(
        NotificationKind.App,
        Banner(
            key = "crash-1",
            tone = StatusTone.Warning,
            icon = Icons.Outlined.ReportProblem,
            eyebrow = "Closed unexpectedly",
            title = "NormPix stopped working last time",
            message = "A report was kept on this phone",
            primary = StatusAction("View", onClick = {}),
            secondary = StatusAction("Dismiss", onClick = {}),
            onOpen = {},
        ),
    )

    /** The font scale the next picture is drawn at: the test changes it and the same composition follows. */
    private var fontScale by mutableFloatStateOf(1f)

    /** The banner [aCardIsHomesBannerPixelForPixel] draws twice: as Home does, and as the page does. */
    private var shown by mutableStateOf<Banner?>(null)

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

    /** Every kind on the page, as banners of Home's design - in a window tall enough that nothing is scrolled out of the picture. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h2600dp-xhdpi")
    fun thePageWithEveryKind() {
        val items = listOf(reminder(), secondReminder(), budget(), card(), backup(), crash(), update())
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

    /** Large text (1.6x): each banner's panels stack, as Home's do. */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h3600dp-xhdpi")
    fun thePageAtLargeText() {
        val items = listOf(reminder(), budget(), backup(), update())
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent {
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, 1.6f),
                    LocalNotifications provides Notifications(items, emptySet()) {},
                ) { Surface(color = MaterialTheme.colorScheme.background) { NotificationsPage() } }
            }
        }
        compose.waitForIdle()
        dump("notifications_page_large_light")
        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        dump("notifications_page_large_dark")
    }

    /**
     * What the page draws for a notification is what Home draws for a banner: the same banner as `BannerStack` of one and as
     * `BannerCard` are compared pixel for pixel - every kind, Light and Dark, normal and large text. The pictures are dumped for
     * looking at too: Home's banner on top, the page's under it.
     */
    @Test
    @Config(sdk = [34], qualifiers = "w360dp-h2000dp-xhdpi")
    fun aCardIsHomesBannerPixelForPixel() {
        val banners = linkedMapOf(
            "reminder" to reminder().banner,
            "budget" to budget().banner,
            "card" to card().banner,
            "backup" to backup().banner,
            "crash" to crash().banner,
            "update" to update().banner,
        )
        compose.setContent {
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        shown?.let { banner ->
                            androidx.compose.foundation.layout.Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                            ) {
                                BannerStack(listOf(banner), rememberDeckStackState(), Modifier.testTag("home"))
                                BannerCard(banner, Modifier.testTag("card"))
                            }
                        }
                    }
                }
            }
        }
        val shifts = listOf(0, 8, 16, 24)
        // What differs at all, kept in the test's output (a hair at large text; nothing at normal).
        val differing = sortedMapOf<String, String>()
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            for (scale in listOf(1f, 1.6f)) {
                env.preferences.setMode(mode)
                compose.runOnIdle { fontScale = scale }
                for ((name, banner) in banners) {
                    compose.runOnIdle { shown = banner }
                    compose.waitForIdle()
                    val home = compose.onNodeWithTag("home").captureToImage().asAndroidBitmap()
                    val page = compose.onNodeWithTag("card").captureToImage().asAndroidBitmap()
                    val label = "$name / $mode / ${scale}x"
                    assertEquals(home.width to home.height, page.width to page.height, "the same size as Home's banner: $label")
                    if (!home.sameAs(page)) {
                        // Say where they differ, and keep both pictures to look at.
                        var count = 0
                        var strongest = 0
                        var left = Int.MAX_VALUE
                        var top = Int.MAX_VALUE
                        var right = -1
                        var bottom = -1
                        for (y in 0 until home.height) for (x in 0 until home.width) {
                            val a = home.getPixel(x, y)
                            val b = page.getPixel(x, y)
                            if (a != b) {
                                count++
                                strongest = maxOf(strongest, shifts.maxOf { shift -> Math.abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) })
                                left = minOf(left, x); top = minOf(top, y); right = maxOf(right, x); bottom = maxOf(bottom, y)
                            }
                        }
                        // Exact at normal text. Large text puts the stacked panels at fractional pixels, where Home's banner (drawn in
                        // its stack's layer) and the page's (drawn straight) round a few edge pixels differently: a hair, not a layout.
                        val allowed = if (scale == 1f) 0 else home.width * home.height / 1000
                        if (count > allowed) {
                            for ((suffix, bitmap) in listOf("home" to home, "page" to page)) {
                                File(outDir, "mismatch_${name}_${mode}_${scale}_$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            }
                            throw AssertionError(
                                "the same pixels as Home's banner: $label - $count differ (at most $allowed), by up to $strongest of 255, " +
                                    "within x $left..$right, y $top..$bottom of ${home.width} x ${home.height}",
                            )
                        }
                        differing["$label"] = "$count pixels, by up to $strongest"
                    }
                    if (name == "reminder" || name == "budget" || (mode == ThemeMode.Light && scale == 1f)) dump("compare_${name}_${mode}_$scale")
                }
            }
        }
        println("Pictures that differ from Home's banner at all (all others are identical): $differing")
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
