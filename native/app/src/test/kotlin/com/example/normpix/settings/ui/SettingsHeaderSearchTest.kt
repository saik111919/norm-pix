package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The main Settings screen's header offers search as an icon only once the search field has scrolled out of view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h600dp")
class SettingsHeaderSearchTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("settings_header_search")
    }

    @After
    fun tearDown() = env.close()

    private fun searchIcon() = compose.onAllNodesWithContentDescription("Search settings")

    private fun searchComesAndGoesWithTheField(style: BackButtonStyle) {
        env.preferences.setBackButtonStyle(style)
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        searchIcon().assertCountEquals(0)

        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            searchIcon().fetchSemanticsNodes().isNotEmpty()
        }

        // Tapping it brings the field back, and the icon goes again.
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            searchIcon().fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun inTheTopFloatingCircleStyle_theSearchIconAppearsOnlyAfterTheFieldScrollsAway() =
        searchComesAndGoesWithTheField(BackButtonStyle.TopFloatingCircle)

    @Test
    fun inTheStandardTopBarStyle_theSearchIconAppearsOnlyAfterTheFieldScrollsAway() =
        searchComesAndGoesWithTheField(BackButtonStyle.StandardTopBar)

    private fun fieldShown() = compose.onAllNodesWithText("Search settings").fetchSemanticsNodes().isNotEmpty()

    private fun waitForIcon(shown: Boolean) = compose.waitUntil(10_000) {
        compose.waitForIdle()
        searchIcon().fetchSemanticsNodes().isNotEmpty() == shown
    }

    /**
     * The page's own title is the first item of the list and the field the second, so "the first item has scrolled" is not "the
     * field has": with the title gone and the field right under the header the icon used to be there already, beside a field
     * that was still on screen. It comes only once the field itself is out of sight - and goes when the field is back.
     */
    @Test
    fun inTheTopFloatingCircleStyle_theIconIsNotThereWhileTheFieldIsStillOnScreenUnderTheGoneTitle() {
        env.preferences.setBackButtonStyle(BackButtonStyle.TopFloatingCircle)
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        val list = compose.onNode(hasScrollToIndexAction())

        // Title scrolled off, the field at the top of the list: it is there to use, so the header offers no second way to it.
        list.performScrollToIndex(1)
        compose.waitForIdle()
        searchIcon().assertCountEquals(0)
        assertTrue(fieldShown(), "the field is still on the page")
        compose.onNodeWithText("Search settings").assertIsDisplayed()

        // Past the field: out of sight, so the icon takes its place.
        list.performScrollToIndex(2)
        waitForIcon(shown = true)

        // Back to the field: the icon goes again.
        list.performScrollToIndex(1)
        waitForIcon(shown = false)
        list.performScrollToIndex(0)
        waitForIcon(shown = false)
    }

    private fun noIconBeforeAnythingHasScrolled(style: BackButtonStyle) {
        env.preferences.setBackButtonStyle(style)
        compose.setContent { env.Content { SettingsScreen() } }
        compose.waitForIdle()
        searchIcon().assertCountEquals(0)
        compose.onNodeWithText("Search settings").assertIsDisplayed()
    }

    @Test
    fun inTheTopFloatingCircleStyle_theIconIsNotThereBeforeAnythingHasScrolled() =
        noIconBeforeAnythingHasScrolled(BackButtonStyle.TopFloatingCircle)

    @Test
    fun inTheStandardTopBarStyle_theIconIsNotThereBeforeAnythingHasScrolled() =
        noIconBeforeAnythingHasScrolled(BackButtonStyle.StandardTopBar)

    @Test
    fun inTheBottomDockStyle_theIconIsNotThereBeforeAnythingHasScrolled() =
        noIconBeforeAnythingHasScrolled(BackButtonStyle.BottomDock)
}
