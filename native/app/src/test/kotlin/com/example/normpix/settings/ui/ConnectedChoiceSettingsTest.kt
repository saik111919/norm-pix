package com.example.normpix.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.normpix.finance.ui.CategoriesPage
import com.example.normpix.settings.ThemeMode
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
 * The theme and the category type are chosen with the app's own connected choice row (the one in the editors), not a
 * platform control: three or two radio-style segments, exactly one selected, and choosing one takes effect at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class ConnectedChoiceSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("connected_choice_settings")
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)

    @Test
    fun theThemeIsThreeRadioSegmentsWithTheCurrentOneSelected() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        // (The page has other radio rows too, such as the page animations.)
        val themeChoices = radio and (hasText("System") or hasText("Light") or hasText("Dark"))
        assertEquals(3, compose.onAllNodes(themeChoices).fetchSemanticsNodes().size, "System, Light and Dark")
        compose.onNode(hasText("System") and radio).assertIsSelected()
        assertEquals(false, compose.onNode(hasText("Dark") and radio).fetchSemanticsNode().config[SemanticsProperties.Selected])
    }

    @Test
    fun choosingAThemeAppliesItAndMovesTheSelection() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()

        compose.onNodeWithText("Dark").performClick()
        compose.waitForIdle()

        assertEquals(ThemeMode.Dark, env.preferences.state.value.mode)
        compose.onNode(hasText("Dark") and radio).assertIsSelected()
        assertEquals(false, compose.onNode(hasText("System") and radio).fetchSemanticsNode().config[SemanticsProperties.Selected])

        compose.onNodeWithText("Light").performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.Light, env.preferences.state.value.mode)
    }

    @Test
    fun theCurrentThemeIsSelectedWhenThePageOpens() {
        env.preferences.setMode(ThemeMode.Light)
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()
        compose.onNode(hasText("Light") and radio).assertIsSelected()
    }

    @Test
    fun categoriesChooseExpenseOrIncomeTheSameWay() {
        compose.setContent { env.Content { CategoriesPage() } }
        compose.waitForIdle()

        assertEquals(2, compose.onAllNodes(radio).fetchSemanticsNodes().size, "Expense and Income")
        compose.onNode(hasText("Expense") and radio).assertIsSelected()

        compose.onNodeWithText("Income").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Income") and radio).assertIsSelected()
        assertEquals(false, compose.onNode(hasText("Expense") and radio).fetchSemanticsNode().config[SemanticsProperties.Selected])
    }
}
