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
import com.example.normpix.ui.theme.RailStyle
import kotlin.test.assertEquals
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

    // ---- the wide rail (a Large window: a tablet in landscape) ----

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun wideRail_putsEachLabelBesideItsIcon_inAColumnOfItsOwnWidth() {
        start()
        val home = bounds("Home")
        val wallet = bounds("Wallet")
        for (label in listOf("Home", "Plans & Activity", "Insights", "Wallet", "Settings")) {
            // The word itself (the rows are one node each, merged, with the icon): after the icon, in the rail.
            val word = compose.onAllNodes(hasText(label), useUnmergedTree = true).onFirst().getUnclippedBoundsInRoot()
            assertTrue(word.right <= AppLayout.wideRailWidth, "$label ends at ${word.right}, past the rail's ${AppLayout.wideRailWidth}")
            // The icon is to the left of the word - 12dp of padding, 16 of the row's, the icon and its gap - not above it, as in the narrow rail.
            assertTrue(word.left > 56.dp, "$label starts at ${word.left}: the icon has the room before it")
            assertEquals(bounds("Home").left.value, bounds(label).left.value, 1f, "$label's row lines up with the others")
        }
        // One row each, in a column; Settings is the last row, at the foot, under the destinations.
        assertTrue(wallet.top > home.bottom)
        assertTrue(bounds("Settings").top > wallet.bottom + 100.dp, "Settings is kept at the foot of the rail")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun wideRail_marksTheDestinationYouAreOn() {
        start()
        fun selected(label: String) = compose.onAllNodes(
            hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).fetchSemanticsNodes().isNotEmpty()
        assertTrue(selected("Home"))
        assertFalse(selected("Wallet"))
        openTab("Wallet")
        assertTrue(selected("Wallet"))
        assertFalse(selected("Home"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun wideRail_hasAnExtendedButtonNamingTheMainAction_andTheOthersBehindMoreActions() {
        start()
        // Home's main action is named on the button, in the rail; its other (income) is in the menu under it.
        assertTrue(bounds("Add expense").right <= AppLayout.wideRailWidth, "the button is in the rail")
        compose.onAllNodes(hasText("Add income")).assertCountEquals(1)
        first("More actions").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("Add income")).assertCountEquals(2)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun wideRail_mainActionFollowsTheTab() {
        start()
        openTab("Wallet")
        assertTrue(bounds("Add card").right <= AppLayout.wideRailWidth)
        compose.onAllNodes(hasText("Add expense")).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun wideRail_foldsDownToTheNarrowOneByHand_andOpensAgain() {
        start()
        val home = bounds("Spent in", substring = true)
        compose.onNodeWithContentDescription("Collapse navigation").performClick()
        compose.waitForIdle()
        // The narrow rail: the round button, labels under their icons inside 96dp, and the button that opens the wide one again.
        compose.onNodeWithContentDescription("Add expense").assertIsDisplayed()
        compose.onAllNodes(hasText("More actions")).assertCountEquals(0)
        assertTrue(bounds("Plans & Activity").right <= AppLayout.railWidth)
        compose.onNodeWithContentDescription("Expand navigation").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Collapse navigation").assertCountEquals(0)
        // The tabs have the room the wide rail left: they start where the narrow rail ends.
        val folded = bounds("Spent in", substring = true)
        assertTrue(folded.left < home.left - 100.dp, "Home moved left by what the rail gave back: ${home.left} to ${folded.left}")
        assertTrue(env!!.container.preferences.state.value.railCollapsed, "the choice is kept")

        compose.onNodeWithContentDescription("Expand navigation").performClick()
        compose.waitForIdle()
        compose.onAllNodes(hasText("More actions")).assertCountEquals(1)
        compose.onNodeWithContentDescription("Collapse navigation").assertIsDisplayed()
        assertEquals(home.left.value, bounds("Spent in", substring = true).left.value, 1f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1280dp-h800dp")
    fun aRailFoldedLastTime_isFoldedAtLaunch() {
        env = TestEnvironment(prefsName = "tablet_layout").also { it.container.preferences.setRailCollapsed(true) }
        val environment = env!!
        compose.setContent { NormPixRoot(container = environment.container) }
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.waitForIdle()
            compose.onAllNodes(hasText("Recent")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Expand navigation").assertIsDisplayed()
        compose.onAllNodes(hasText("More actions")).assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h768dp")
    fun anExpandedTablet_keepsTheNarrowRail_withNothingToFoldOrOpen() {
        start()
        compose.onNodeWithContentDescription("Add expense").assertIsDisplayed()
        compose.onAllNodes(hasText("More actions")).assertCountEquals(0)
        assertTrue(bounds("Plans & Activity").right <= AppLayout.railWidth)
        compose.onAllNodesWithContentDescription("Expand navigation").assertCountEquals(0)
        compose.onAllNodesWithContentDescription("Collapse navigation").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w915dp-h412dp")
    fun aPhoneOnItsSide_keepsTheNarrowRail_howeverWideItIs() {
        start()
        compose.onAllNodes(hasText("More actions")).assertCountEquals(0)
        assertTrue(bounds("Home").right <= AppLayout.railWidth)
    }

    @Test
    fun railStyle_followsTheWindow() {
        assertEquals(RailStyle.None, RailStyle.of(411, 891))
        assertEquals(RailStyle.Compact, RailStyle.of(800, 1280))
        assertEquals(RailStyle.Compact, RailStyle.of(1024, 768))
        assertEquals(RailStyle.Wide, RailStyle.of(1280, 800))
        assertEquals(RailStyle.Compact, RailStyle.of(1280, 400), "a wide window too short for the wide rail's rows")
        assertEquals(RailStyle.Compact, RailStyle.of(915, 412))
        // Folded by hand: only a window that has the wide rail has one to fold, and the dock is the dock whatever was chosen.
        assertEquals(RailStyle.Compact, RailStyle.of(1280, 800, collapsed = true))
        assertEquals(RailStyle.None, RailStyle.of(411, 891, collapsed = true))
        assertEquals(RailStyle.Compact, RailStyle.of(800, 1280, collapsed = true))
        assertTrue(RailStyle.canBeWide(1280, 800))
        assertFalse(RailStyle.canBeWide(1024, 768))
        assertFalse(RailStyle.canBeWide(1280, 400))
        // What the tabs have left: the rule for their columns reads this.
        assertEquals(AppLayout.wideRailWidth, RailStyle.Wide.width)
        assertEquals(0.dp, RailStyle.None.width)
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
        // With 1024dp beside the wide rail and 840dp of it used, there is about 90dp of margin before the list.
        assertTrue(activity.left > AppLayout.wideRailWidth + 60.dp, "the list starts at ${activity.left}: not centred in what the rail leaves")
    }

    // ---- the rule itself ----

    @Test
    fun twoColumns_startAt760dp() {
        assertFalse(isTwoColumn(759.dp))
        assertTrue(isTwoColumn(760.dp))
        // What a tab has after the rail and its own padding: a landscape tablet (1280, with the wide rail) has room for two columns,
        // an upright one (800, with the narrow one) not.
        assertTrue(isTwoColumn(1280.dp - AppLayout.wideRailWidth - 32.dp))
        assertFalse(isTwoColumn(800.dp - AppLayout.railWidth - 32.dp))
    }

    private companion object {
        /** What the Wallet's cards header says under it on an empty wallet. */
        const val NO_CARDS_YET = "None saved yet. Encrypted, and kept on this device"
    }
}
