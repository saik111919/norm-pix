package com.example.normpix.settings.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.testing.TestEnvironment
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Settings > Appearance > Colors is a split row, as Pixel's Settings does "Adaptive brightness": the row opens the Colors page,
 * and a switch at its end, after a divider, chooses between the wallpaper's colors and the user's own without opening it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h900dp")
class ColorsSwitchRowTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("colors_switch_row")
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent { env.Content { AppearanceSettingsPage() } }
        compose.waitForIdle()
    }

    private val wallpaperSwitch get() = compose.onNodeWithContentDescription("Use wallpaper colors")

    private val prefs get() = env.preferences.state.value

    @Test
    fun theColorsRowCarriesASwitch_onForWallpaperColorsUntilTheUserChoosesOwn() {
        show()

        wallpaperSwitch.assertIsOn()
        compose.onNodeWithText("Wallpaper colors").assertExists()
        // It is the only switch on the page: the other rows open pages, and say so with a chevron.
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
    }

    @Test
    fun theSwitchChoosesBetweenWallpaperAndOwnColors_withoutOpeningThePage() {
        show()

        wallpaperSwitch.performClick()
        compose.waitForIdle()
        assertEquals(ColorSource.Custom, prefs.colorSource)
        wallpaperSwitch.assertIsOff()
        compose.onNodeWithText("Volt accent").assertExists()

        wallpaperSwitch.performClick()
        compose.waitForIdle()
        assertEquals(ColorSource.System, prefs.colorSource)
        wallpaperSwitch.assertIsOn()
        compose.onNodeWithText("Wallpaper colors").assertExists()

        assertEquals(emptyList(), env.navigator.appearanceSections, "the switch never opened the Colors page")
    }

    @Test
    fun theRowItselfOpensTheColorsPage_leavingTheChoiceAlone() {
        show()

        compose.onNodeWithText("Colors").performClick()
        compose.waitForIdle()

        assertEquals(listOf(AppearanceSection.Colors), env.navigator.appearanceSections)
        assertEquals(ColorSource.System, prefs.colorSource)
        wallpaperSwitch.assertIsOn()
    }

    @Test
    fun anAccentPickedBeforeShowsAsTheirOwnColorsWithTheSwitchOff() {
        env.preferences.setAccent(AccentColor.Blue)
        show()

        wallpaperSwitch.assertIsOff()
        compose.onNodeWithText("Ocean accent").assertExists()
    }

    @Test
    fun theSwitchSitsAtTheEndOfTheRow_afterTheName() {
        show()

        val name = compose.onNodeWithText("Colors").getBoundsInRoot()
        val switch = wallpaperSwitch.getBoundsInRoot()
        val screen = compose.onRoot().getBoundsInRoot()
        assertTrue(name.right <= switch.left, "the name $name is before the switch $switch")
        assertTrue(screen.right - switch.right <= 40.dp, "the switch is at the end of the row: $switch in $screen")
    }
}
