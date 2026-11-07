package com.example.normpix.ui

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.IconStyle
import com.example.normpix.settings.ui.AppearanceSectionPage
import com.example.normpix.testing.TestEnvironment
import kotlin.test.assertEquals
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings > Appearance > Icons offers the two icon sets, Material selected to start with, and the pick takes effect at once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class IconStyleChoiceTest {
    @get:Rule
    val compose = createComposeRule()

    private val env = TestEnvironment("icon_style_choice_test")

    @After
    fun tearDown() = env.close()

    @Test
    fun offersBothSets_materialSelectedToStartWith() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.CategoryIcons) } }
        compose.waitForIdle()
        compose.onNodeWithText("Interface icons").assertExists()
        compose.onNodeWithText("Material").performScrollTo().assertIsSelected()
        compose.onNodeWithText("Lucide").performScrollTo()
    }

    @Test
    fun choosingLucideTakesEffectAtOnce_andMaterialBringsItBack() {
        compose.setContent { env.Content { AppearanceSectionPage(AppearanceSection.CategoryIcons) } }
        compose.waitForIdle()

        compose.onNodeWithText("Lucide").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(IconStyle.Lucide, env.preferences.state.value.iconStyle)
        compose.onNodeWithText("Lucide").assertIsSelected()

        compose.onNodeWithText("Material").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(IconStyle.Material, env.preferences.state.value.iconStyle)
        compose.onNodeWithText("Material").assertIsSelected()
    }
}
