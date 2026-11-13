package com.example.normpix.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.BannerColors
import com.example.normpix.ui.components.bannerColors
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Needs attention banners say their status in colour (owner, 2026-11-01: the calm version was "not informative"): every tone's
 * surface is washed with its colour, so an overdue banner and a budget banner differ, and every text on it - the eyebrow, the quieter
 * button, the main button on its tint - still reads at 4.5:1, in Light and Dark and every accent.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BannerColorsTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment("banner_colors_test")
    }

    @After
    fun tearDown() = env.close()

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    @Test
    fun `every tone reads in every theme, and the tones are told apart`() {
        val seen = mutableMapOf<String, Map<StatusTone, BannerColors>>()
        var surfaceContainer = Color.Unspecified
        compose.setContent {
            env.Content {
                val scheme = MaterialTheme.colorScheme
                surfaceContainer = scheme.surfaceContainer
                val prefs by env.preferences.state.collectAsState()
                seen["${prefs.mode}/${prefs.accent}"] = StatusTone.entries.associateWith { bannerColors(it.resolve(), scheme) }
            }
        }
        env.preferences.setColorSource(ColorSource.Custom)
        for (mode in listOf(ThemeMode.Light, ThemeMode.Dark)) {
            for (accent in AccentColor.entries) {
                env.preferences.setMode(mode)
                env.preferences.setAccent(accent)
                compose.waitForIdle()
                val colors = assertNotNullOrFail(seen["$mode/$accent"], "$mode/$accent was drawn")
                for ((tone, c) in colors) {
                    val at = "$mode/$accent/$tone"
                    assertTrue(contrast(c.accent, c.surface) >= 4.5f, "$at: the eyebrow reads on the banner (${contrast(c.accent, c.surface)})")
                    assertTrue(contrast(c.buttonInk, c.buttonFill) >= 4.5f, "$at: the tinted button's label reads (${contrast(c.buttonInk, c.buttonFill)})")
                    // The main button of the bar's group is the status colour itself (2026-11-11).
                    assertTrue(contrast(c.onSolid, c.solid) >= 4.5f, "$at: the main button's label reads on the solid colour (${contrast(c.onSolid, c.solid)})")
                    assertTrue(contrast(c.icon, c.accent) >= 3f, "$at: the icon reads on its disc (${contrast(c.icon, c.accent)})")
                }
                // Coloured, not grey: an overdue banner's surface is not a warning's, and neither is the page's plain surface.
                assertNotEquals(colors.getValue(StatusTone.Error).surface, colors.getValue(StatusTone.Warning).surface, "$mode/$accent")
                assertNotEquals(surfaceContainer, colors.getValue(StatusTone.Error).surface, "$mode/$accent")
            }
        }
    }

    private fun <T : Any> assertNotNullOrFail(value: T?, message: String): T = value ?: throw AssertionError(message)
}
