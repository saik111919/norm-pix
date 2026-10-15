package com.example.normpix.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.CategoryIconShape
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Appearance > Icon background: the four shapes behind category icons and people, one chosen, the choice taking
 * effect at once. And the main Settings screen's header: its search icon (and the pill it sits in) is there only once the
 * search field has scrolled away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class IconBackgroundSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("icon_background_settings")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    private fun shape(label: String) = compose.onNode(hasText(label) and radio)

    @Test
    fun appearanceOffersEveryShape_withTheCircleChosen() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("Icon background").assertExists()
        for (shape in CategoryIconShape.entries) shape(shape.label).assertExists()
        shape("Circle").assertIsSelected()
        shape("Rounded").assertIsNotSelected()
        shape("Square").assertIsNotSelected()
        shape("No background").assertIsNotSelected()
    }

    @Test
    fun choosingAShapeAppliesItAtOnce_andMovesTheSelection() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        shape("Square").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(CategoryIconShape.Square, env.preferences.state.value.categoryIconShape)
        shape("Square").assertIsSelected()
        shape("Circle").assertIsNotSelected()

        shape("No background").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(CategoryIconShape.None, env.preferences.state.value.categoryIconShape)
        shape("No background").assertIsSelected()
    }

    @Test
    fun theCurrentShapeIsSelectedWhenThePageOpens() {
        env.preferences.setCategoryIconShape(CategoryIconShape.Rounded)
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        shape("Rounded").assertIsSelected()
        shape("Circle").assertIsNotSelected()
    }
}

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
