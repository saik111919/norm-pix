package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
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
}
