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
import com.example.normpix.ui.components.StatusAction
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
            eyebrow = "Overdue by 2 days",
            title = "Pay the electricity bill",
            message = "Was due on 6 November",
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
            title = "Food & dining",
            message = "Rs 1,200 left of Rs 8,000",
            primary = StatusAction("Adjust", onClick = {}),
            bar = BannerBar(0.85f, "85% used"),
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
            eyebrow = "Backup due",
            title = "Last backup 30 days ago",
            message = "Keep a copy on this phone or another",
            primary = StatusAction("Back up", onClick = {}),
            figure = BannerFigure("30", "days", "since the last one"),
            onOpen = {},
        ),
    )

    @Test
    fun thePageWithFourKinds() {
        val items = listOf(reminder(), budget(), card(), backup())
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
