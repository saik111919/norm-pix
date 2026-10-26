package com.example.normpix.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.normpix.finance.ui.AccountHistoryPage
import com.example.normpix.finance.ui.TransactionHistory
import com.example.normpix.navigation.DockFootprint
import com.example.normpix.settings.ui.SettingsScreen
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.LocalDockGone
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Bottom controls: with the setting on, a phone's pages have what they would have at the top - the way back, their own
 * buttons, their search and filters - at the bottom instead, within a thumb's reach; a wide window ignores the setting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class BottomControlsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("bottom_controls_test")
    }

    @After
    fun tearDown() = env.close()

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { env.Content(content) }
        compose.waitForIdle()
    }

    private val screenHeight: Dp get() = compose.onRoot().getBoundsInRoot().height

    private fun SemanticsNodeInteraction.top(): Dp = getBoundsInRoot().top

    private fun SemanticsNodeInteraction.bottom(): Dp = getBoundsInRoot().bottom

    /** The page's one text field: its search. */
    private fun searchField() = compose.onNode(hasSetTextAction())

    private fun back() = compose.onNode(hasContentDescription("Back", substring = true))

    private fun accountId() = env.container.finance.state.value.defaultAccount!!.id!!

    private fun assertInUpperHalf(node: SemanticsNodeInteraction, what: String) =
        assertTrue(node.top() < screenHeight / 2, "$what is at the top: ${node.top()} of $screenHeight")

    private fun assertInLowerQuarter(node: SemanticsNodeInteraction, what: String) =
        assertTrue(node.top() > screenHeight * 0.75f, "$what is at the foot: ${node.top()} of $screenHeight")

    @Test
    fun aPushedHistoryHasItsBackButtonActionsAndSearchAtTheTop_untilTheSettingIsOn() {
        show { AccountHistoryPage(accountId()) }

        assertInUpperHalf(back(), "the way back")
        assertInUpperHalf(compose.onNodeWithContentDescription("Account options"), "the account's options")
        assertInUpperHalf(searchField(), "the search field")
    }

    @Test
    fun aPushedHistoryMovesBackItsButtonsAndItsSearchToTheBottom_withTheSearchAboveTheBackRow() {
        env.preferences.setBottomControls(true)
        show { AccountHistoryPage(accountId()) }

        assertInLowerQuarter(back(), "the way back")
        assertInLowerQuarter(compose.onNodeWithContentDescription("Account options"), "the account's options")
        assertInLowerQuarter(searchField(), "the search field")
        assertTrue(searchField().bottom() <= back().top(), "the search card rests on the back row: ${searchField().bottom()} vs ${back().top()}")
        // The page's own buttons stand in for the tile that opens Search.
        compose.onAllNodesWithContentDescription("Search").assertCountEquals(0)
        // The search left the list: there is the one field, and it is the card's.
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    }

    @Test
    fun theBottomRowSpansTheWidth_backAtTheStart_andThePagesButtonsAtTheEnd() {
        env.preferences.setBottomControls(true)
        show { AccountHistoryPage(accountId()) }
        val width = compose.onRoot().getBoundsInRoot().right

        assertTrue(back().getBoundsInRoot().left < 24.dp, "back starts where the search card does: ${back().getBoundsInRoot().left}")
        val options = compose.onNodeWithContentDescription("Account options").getBoundsInRoot()
        assertTrue(options.right > width - 32.dp, "the buttons end where it ends: ${options.right} of $width")
    }

    @Test
    fun theBackButtonStillGoesBack_fromTheBottom() {
        env.preferences.setBottomControls(true)
        show { AccountHistoryPage(accountId()) }

        back().performClickAndWait()

        assertEquals(1, env.navigator.backs)
    }

    @Test
    fun aTabsSearchSitsAtTheFootOfThePage_withNoBackButtonToGoWith() {
        env.preferences.setBottomControls(true)
        show { TransactionHistory(title = "Activity") }

        assertInLowerQuarter(searchField(), "the search field")
        compose.onAllNodes(hasContentDescription("Back", substring = true)).assertCountEquals(0)
    }

    @Test
    fun aTabsSearchStaysInItsListWhileTheSettingIsOff() {
        show { TransactionHistory(title = "Activity") }

        assertInUpperHalf(searchField(), "the search field")
    }

    @Test
    fun aTabsSearchCardGoesDownIntoTheDocksPlace_asTheDockStepsOut() {
        env.preferences.setBottomControls(true)
        var gone by mutableFloatStateOf(0f)
        show {
            CompositionLocalProvider(LocalDockGone provides { gone }) { TransactionHistory(title = "Activity") }
        }
        val onTheDock = searchField().top()

        gone = 1f
        compose.waitForIdle()
        val settled = searchField().top()

        val moved = settled - onTheDock
        assertTrue(moved > DockFootprint, "the card went down by the dock's room: $moved, the dock is $DockFootprint")
        assertTrue(moved < DockFootprint + 24.dp, "and no further: $moved")
    }

    @Test
    fun settingsKeepsItsSearchAtTheTop_untilTheSettingIsOn_thenItIsAtTheBottomAndTheHeaderIconIsGone() {
        show { SettingsScreen() }
        assertInUpperHalf(searchField(), "the search field")

        env.preferences.setBottomControls(true)
        compose.waitForIdle()

        assertInLowerQuarter(searchField(), "the search field")
        assertInLowerQuarter(back(), "the way back")
        // Nothing scrolls the field away any more, so nothing offers to bring it back.
        compose.onAllNodesWithContentDescription("Search settings").assertCountEquals(0)
    }

    @Test
    @Config(sdk = [35], qualifiers = "w1280dp-h800dp")
    fun aWideWindowKeepsItsLayoutWhateverTheSetting() {
        env.preferences.setBottomControls(true)
        show { AccountHistoryPage(accountId()) }

        assertInUpperHalf(back(), "the way back")
        assertInUpperHalf(searchField(), "the search field")
        assertFalse(searchField().top() > screenHeight * 0.75f)
    }

    private fun SemanticsNodeInteraction.performClickAndWait() {
        performClick()
        compose.waitForIdle()
    }
}
