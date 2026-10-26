package com.example.normpix.settings.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.SheetAnimationType
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
 * Settings > Appearance > Transitions, under Sheets & dialogs: every style, the Linux window one among them (a desktop window's opening
 * and closing, as the pages can have), one chosen and taking effect at once - and the samples under "Try it" still open and
 * close a sheet and a question in it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class SheetTransitionsSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("sheet_transitions_settings")
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.Transitions) } }
        compose.waitForIdle()
    }

    @After
    fun tearDown() = env.close()

    private val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
    // The pages' styles share the page, and two names ("Slide", "Linux window"): a sheet style is known by what it says it does.
    private fun style(label: String) = compose.onNode(hasText(SheetAnimationType.entries.single { it.label == label }.description) and radio)

    @Test
    fun theLinuxWindowIsOneOfTheStyles_withItsDescription() {
        for (type in SheetAnimationType.entries) style(type.label).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(SheetAnimationType.LinuxWindow.description).performScrollTo().assertIsDisplayed()
        // The window style the sheets had before is still chosen: the new one is a choice, not a change.
        style("Window").assertIsSelected()
        style("Linux window").assertIsNotSelected()
    }

    @Test
    fun choosingTheLinuxWindowAppliesAtOnce_andIsKept() {
        style("Linux window").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(SheetAnimationType.LinuxWindow, env.preferences.state.value.sheetAnimation)
        style("Linux window").assertIsSelected()
        style("Window").assertIsNotSelected()
        assertEquals(
            SheetAnimationType.LinuxWindow,
            com.example.normpix.settings.AppPreferences(env.prefsStore).state.value.sheetAnimation,
            "read back by the next launch",
        )
    }

    @Test
    fun aSheetOpensAndClosesInTheLinuxWindowStyle() {
        env.preferences.setSheetAnimation(SheetAnimationType.LinuxWindow)
        compose.waitForIdle()

        compose.onNodeWithText("Open a sheet").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("A sheet").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("A sheet").assertIsDisplayed()

        compose.onNodeWithText("Done").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("A sheet").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aQuestionOpensAndAnswersInTheLinuxWindowStyle_aDialogIsTheSameSurface() {
        env.preferences.setSheetAnimation(SheetAnimationType.LinuxWindow)
        compose.waitForIdle()

        compose.onNodeWithText("Open a question").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("A question").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("A question").assertIsDisplayed()

        compose.onNodeWithText("No").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("A question").fetchSemanticsNodes().isEmpty() }
    }
}
