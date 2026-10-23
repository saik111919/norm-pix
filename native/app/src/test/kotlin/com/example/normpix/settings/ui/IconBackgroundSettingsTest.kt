package com.example.normpix.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
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
 * Settings > Appearance > Category icons > Icon background: the four shapes behind category icons and people, one chosen, the
 * choice taking effect at once.
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
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.CategoryIcons) } }
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
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.CategoryIcons) } }
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
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.CategoryIcons) } }
        compose.waitForIdle()

        shape("Rounded").assertIsSelected()
        shape("Circle").assertIsNotSelected()
    }
}
