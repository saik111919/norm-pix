package com.example.normpix.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.isTwoColumn
import com.example.normpix.ui.theme.AppLayout
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app on a tablet: a rail in place of the dock, with the way to add things in it, and the tabs laid out for the room they
 * have - two columns where there is room for two (Home, Wallet), one wider column for the transaction list, one column when the
 * tablet is held upright and the rail leaves only a phone's width.
 */
@RunWith(RobolectricTestRunner::class)
// Real text measurement: a label that is too wide for the rail only runs over its edge with the font's real widths.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp")
class TabletLayoutTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    /** The app on an empty ledger, with [shown] on screen (it loads in the background, so this waits for it). */
    private fun start(shown: String = "Recent") {
        val environment = TestEnvironment(prefsName = "tablet_layout").also { env = it }
        compose.setContent { NormPixRoot(container = environment.container) }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText(shown)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openTab(label: String) {
        compose.onAllNodes(hasText(label)).onFirst().performClick()
        compose.waitForIdle()
    }

    private fun first(text: String, substring: Boolean = false): SemanticsNodeInteraction =
        compose.onAllNodes(hasText(text, substring = substring)).onFirst()

    private fun bounds(text: String, substring: Boolean = false): DpRect = first(text, substring).getUnclippedBoundsInRoot()

    // ---- the rail ----

    @Test
    fun rail_labelsStayInsideTheRail() {
        start()
        for (label in listOf("Home", "Plans & Activity", "Insights", "Wallet", "Settings")) {
            val b = bounds(label)
            assertTrue(b.left >= 0.dp, "$label starts at ${b.left}, off the rail's left edge")
            assertTrue(b.right <= AppLayout.railWidth, "$label ends at ${b.right}, past the rail's ${AppLayout.railWidth}")
        }
    }

    @Test
    fun rail_hasTheMainActionAndTheOthersBehindMore() {
        start()
        // Home's main action is the rail's button; its other (income) is in the menu under it, besides Home's own button.
        compose.onNodeWithContentDescription("Add expense").assertIsDisplayed()
        compose.onAllNodes(hasText("Add income")).assertCountEquals(1)
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("Add income")).assertCountEquals(2)
    }

    @Test
    fun rail_mainActionFollowsTheTab() {
        start()
        openTab("Wallet")
        // The wallet's main action is a card; an account is behind More.
        compose.onNodeWithContentDescription("Add card").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Add expense").assertCountEquals(0)
        val onPage = compose.onAllNodes(hasText("Add account")).fetchSemanticsNodes().size
        compose.onNodeWithContentDescription("More actions").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("Add account")).assertCountEquals(onPage + 1)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    fun phone_keepsTheDock_withoutTheRailsButton() {
        start()
        compose.onAllNodesWithContentDescription("Add expense").assertCountEquals(0)
    }

    // ---- Home ----

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun home_isTwoColumns_whenTheTabHasRoom() {
        start()
        val month = bounds("Spent in", substring = true)
        val recent = bounds("Recent")
        assertTrue(recent.left - month.left > 300.dp, "Recent is a column over from the month: ${recent.left} vs ${month.left}")
        assertTrue(recent.top < month.bottom, "Recent sits beside the month, not under it: ${recent.top} vs ${month.bottom}")
    }

    @Test
    fun home_isOneColumn_whenTheTabletIsUpright() {
        start()
        val month = bounds("Spent in", substring = true)
        val recent = bounds("Recent")
        assertTrue(recent.left - month.left < 100.dp, "Recent is in the month's column: ${recent.left} vs ${month.left}")
        assertTrue(recent.top > month.bottom, "Recent is under the month: ${recent.top} vs ${month.bottom}")
    }

    // ---- Wallet ----

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun wallet_putsAccountsAndCardsSideBySide_whenWide() {
        start()
        openTab("Wallet")
        val accounts = bounds("Accounts")
        val cards = bounds(NO_CARDS_YET)
        assertTrue(cards.left - accounts.left > 300.dp, "the cards are a column over from the accounts: ${cards.left} vs ${accounts.left}")
        // Their headers open the two columns together.
        assertTrue(cards.top - accounts.top < 60.dp, "the cards' header is level with the accounts': ${cards.top} vs ${accounts.top}")
    }

    @Test
    fun wallet_stacksCardsUnderAccounts_whenUpright() {
        start()
        openTab("Wallet")
        val accounts = bounds("Accounts")
        val cards = bounds(NO_CARDS_YET)
        assertTrue(cards.left - accounts.left < 100.dp, "one column: ${cards.left} vs ${accounts.left}")
        assertTrue(cards.top > accounts.bottom, "the cards come after the accounts: ${cards.top} vs ${accounts.bottom}")
    }

    // ---- Activity ----

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun activity_isOneColumn_capped_andCentred() {
        start()
        openTab("Plans & Activity")
        val activity = compose.onNode(
            hasText("Activity") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
        ).getUnclippedBoundsInRoot()
        val plans = compose.onNode(
            hasText("Plans") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription),
        ).getUnclippedBoundsInRoot()
        // The two views' switch is as wide as the list, less the page's 16dp on each side: wider than the 720dp column forms
        // use, within the 840dp this list is capped at.
        val width = plans.right - activity.left + 32.dp
        assertTrue(width > AppLayout.maxContentWidth, "the list is $width wide, no wider than a form's ${AppLayout.maxContentWidth}")
        assertTrue(width <= AppLayout.maxListWidth, "the list is $width wide, past ${AppLayout.maxListWidth}")
        // With 1184dp beside the rail and 840dp of it used, there is more than 150dp of margin before the list.
        assertTrue(activity.left > AppLayout.railWidth + 150.dp, "the list starts at ${activity.left}: not centred in what the rail leaves")
    }

    // ---- the rule itself ----

    @Test
    fun twoColumns_startAt760dp() {
        assertFalse(isTwoColumn(759.dp))
        assertTrue(isTwoColumn(760.dp))
        // What a tab has after the rail and its own padding: a landscape tablet (1280) has room for two columns, an upright one (800) not.
        assertTrue(isTwoColumn(1280.dp - AppLayout.railWidth - 32.dp))
        assertFalse(isTwoColumn(800.dp - AppLayout.railWidth - 32.dp))
    }

    private companion object {
        /** What the Wallet's cards header says under it on an empty wallet. */
        const val NO_CARDS_YET = "None saved yet. Encrypted, and kept on this device"
    }
}
