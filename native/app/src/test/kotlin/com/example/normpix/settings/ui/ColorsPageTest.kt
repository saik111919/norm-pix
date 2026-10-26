package com.example.normpix.settings.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.normpix.navigation.AppearanceSection
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.ColorSource
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
 * Settings > Appearance > Colors, after Pixel's wallpaper & style picker: a switch between the wallpaper's colors and other
 * colors, and under it each palette as a tile showing the colors it gives the app - the chosen one ringed. Choosing one applies it
 * at once, and Pure (black and white) is among them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
class ColorsPageTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var env: TestEnvironment
    private var scheme: ColorScheme? = null

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("colors_page")
    }

    @After
    fun tearDown() = env.close()

    private fun show() {
        compose.setContent {
            env.Content {
                scheme = MaterialTheme.colorScheme
                AppearanceSectionPage(AppearanceSection.Colors)
            }
        }
        compose.waitForIdle()
    }

    private val prefs get() = env.preferences.state.value

    private fun tile(label: String) = compose.onNodeWithContentDescription("$label color")

    @Test
    fun theWallpaperColorsComeFirst_asTheOneTileTheWallpaperMakes() {
        show()

        compose.onNodeWithText("Wallpaper colors").assertExists()
        compose.onNodeWithText("Other colors").assertExists()
        compose.onNodeWithText("Icons, text and more match the colors in your wallpaper").assertExists()
        tile(WALLPAPER_TILE).assertIsSelected()
        for (accent in AccentColor.entries) compose.onAllNodesWithContentDescription("${accent.label} color").assertCountEquals(0)
    }

    @Test
    fun otherColorsShowEveryPaletteAsATile_withTheChosenOneSelected() {
        show()

        compose.onNodeWithText("Other colors").performClick()
        compose.waitForIdle()

        assertEquals(ColorSource.Custom, prefs.colorSource)
        compose.onAllNodesWithContentDescription("$WALLPAPER_TILE color").assertCountEquals(0)
        for (accent in AccentColor.entries) tile(accent.label).performScrollTo().assertExists()
        tile(AccentColor.Volt.label).assertIsSelected()
        for (accent in AccentColor.entries - AccentColor.Volt) tile(accent.label).assertIsNotSelected()
    }

    @Test
    fun choosingAPaletteAppliesItAtOnce_andMovesTheSelection() {
        env.preferences.setAccent(AccentColor.Volt)
        show()

        tile("Ocean").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(AccentColor.Blue, prefs.accent)
        assertEquals(ColorSource.Custom, prefs.colorSource)
        tile("Ocean").assertIsSelected()
        tile("Volt").assertIsNotSelected()
        assertEquals(AccentColor.Blue, AppPreferences(env.prefsStore).state.value.accent, "kept across a restart")
    }

    @Test
    fun pureIsOneOfThePalettes_andDrawsTheAppInBlackAndWhite() {
        env.preferences.setMode(ThemeMode.Light)
        env.preferences.setAccent(AccentColor.Volt)
        show()

        tile("Pure").performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals(AccentColor.Pure, prefs.accent)
        tile("Pure").assertIsSelected()
        assertEquals(Color.White, scheme?.surface, "light: a white page")
        assertEquals(Color.Black, scheme?.onSurface, "light: black ink")

        env.preferences.setMode(ThemeMode.Dark)
        compose.waitForIdle()
        assertEquals(Color.Black, scheme?.surface, "dark: a black page")
        assertEquals(Color.White, scheme?.onSurface, "dark: white ink")
        tile("Pure").assertIsSelected()
    }

    @Test
    fun theSwitchGoesBackToTheWallpaper_keepingTheOtherChoiceForNextTime() {
        env.preferences.setAccent(AccentColor.Purple)
        show()
        compose.onNodeWithText("Wallpaper colors").performClick()
        compose.waitForIdle()

        assertEquals(ColorSource.System, prefs.colorSource)
        assertEquals(AccentColor.Purple, prefs.accent, "the accent is kept")
        tile(WALLPAPER_TILE).assertIsSelected()

        compose.onNodeWithText("Other colors").performClick()
        compose.waitForIdle()
        tile("Royal").assertIsSelected()
    }

    @Test
    fun thePageOpensOnTheSideInUse() {
        env.preferences.setAccent(AccentColor.Red)
        show()

        tile("Coral").assertIsSelected()
        compose.onAllNodesWithText("Icons, text and more match the colors in your wallpaper").assertCountEquals(0)
    }
}
