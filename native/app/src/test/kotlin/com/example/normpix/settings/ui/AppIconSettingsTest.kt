package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.AppIcon
import com.example.normpix.settings.AppIcons
import com.example.normpix.testing.TestEnvironment
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Appearance > App icon (its own screen): every icon on offer, the one in the launcher picked, and a change asked about first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class AppIconSettingsTest {
    @get:Rule
    val compose = createComposeRule()

    private val env = TestEnvironment("app_icon_settings")

    @After
    fun tearDown() = env.close()

    private fun open() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.AppIcon) } }
        compose.waitForIdle()
    }

    private fun icon(icon: AppIcon) = compose.onNodeWithContentDescription("${icon.label} icon").performScrollTo()

    @Test
    fun everyIconIsOffered_theOneInTheLauncherPicked() {
        open()
        for (each in AppIcon.entries) icon(each).assertIsDisplayed()
        icon(AppIcon.Default).assertIsSelected()
        icon(AppIcon.Comic).assertIsNotSelected()
    }

    @Test
    fun theIconChosenBeforeIsPickedWhenThePageOpens() {
        AppIcons.use(env.context, AppIcon.Paper)
        open()
        icon(AppIcon.Paper).assertIsSelected()
        icon(AppIcon.Default).assertIsNotSelected()
    }

    @Test
    fun pickingAnIconAsksFirst_andChangesItOnlyOnYes() {
        open()

        icon(AppIcon.Comic).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Use the Comic icon?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Use the Comic icon?").fetchSemanticsNodes().isEmpty() }
        assertEquals(AppIcon.Default, AppIcons.current(env.context), "cancel leaves the launcher alone")
        icon(AppIcon.Default).assertIsSelected()

        icon(AppIcon.Comic).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Change icon").performClick()
        compose.waitUntil(5_000) {
            compose.waitForIdle()
            AppIcons.current(env.context) == AppIcon.Comic
        }
        icon(AppIcon.Comic).assertIsSelected()
        icon(AppIcon.Default).assertIsNotSelected()
    }
}
