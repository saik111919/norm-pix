package com.example.normpix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import kotlin.math.max
import kotlin.math.min
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pure, the black and white theme: a white page with black ink in light and a black page with white ink in dark, greys between
 * and no hue but the error red; every pair of text and ground reads in both modes, the surfaces step the right way, and it follows
 * the Theme choice (light, dark, system) like every other color.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PureThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private val white = Color.White
    private val black = Color.Black
    private val modes = listOf(false, true)

    private fun contrast(a: Color, b: Color): Double {
        val hi = max(a.luminance(), b.luminance()).toDouble()
        val lo = min(a.luminance(), b.luminance()).toDouble()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun isGrey(c: Color) = c.red == c.green && c.green == c.blue

    @Test
    fun thePageIsPureWhiteInLightAndPureBlackInDark_withTheInkTheOtherWay() {
        val light = AppTheme.accentScheme(AccentColor.Pure, isDark = false)
        assertEquals(white, light.surface)
        assertEquals(white, light.background)
        assertEquals(black, light.onSurface)
        assertEquals(black, light.primary)
        assertEquals(white, light.onPrimary)

        val dark = AppTheme.accentScheme(AccentColor.Pure, isDark = true)
        assertEquals(black, dark.surface)
        assertEquals(black, dark.background)
        assertEquals(white, dark.onSurface)
        assertEquals(white, dark.primary)
        assertEquals(black, dark.onPrimary)
    }

    @Test
    fun everyColorOfItIsAGrey_exceptTheErrorRed() {
        for (dark in modes) {
            val s = AppTheme.accentScheme(AccentColor.Pure, dark)
            val greys = mapOf(
                "primary" to s.primary, "onPrimary" to s.onPrimary, "primaryContainer" to s.primaryContainer,
                "onPrimaryContainer" to s.onPrimaryContainer, "secondary" to s.secondary, "onSecondary" to s.onSecondary,
                "secondaryContainer" to s.secondaryContainer, "onSecondaryContainer" to s.onSecondaryContainer,
                "tertiary" to s.tertiary, "onTertiary" to s.onTertiary, "tertiaryContainer" to s.tertiaryContainer,
                "onTertiaryContainer" to s.onTertiaryContainer, "background" to s.background, "surface" to s.surface,
                "onSurface" to s.onSurface, "surfaceVariant" to s.surfaceVariant, "onSurfaceVariant" to s.onSurfaceVariant,
                "outline" to s.outline, "outlineVariant" to s.outlineVariant, "inverseSurface" to s.inverseSurface,
                "inverseOnSurface" to s.inverseOnSurface, "inversePrimary" to s.inversePrimary, "surfaceTint" to s.surfaceTint,
                "surfaceBright" to s.surfaceBright, "surfaceDim" to s.surfaceDim, "surfaceContainerLowest" to s.surfaceContainerLowest,
                "surfaceContainerLow" to s.surfaceContainerLow, "surfaceContainer" to s.surfaceContainer,
                "surfaceContainerHigh" to s.surfaceContainerHigh, "surfaceContainerHighest" to s.surfaceContainerHighest,
            )
            for ((name, color) in greys) assertTrue(isGrey(color), "${if (dark) "dark" else "light"} $name is not a grey: $color")
        }
    }

    /** Every text role with the ground it sits on: the pairs the app's components draw. */
    private fun textPairs(s: ColorScheme): Map<String, Pair<Color, Color>> = mapOf(
        "onPrimary on primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer on primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondary on secondary" to (s.onSecondary to s.secondary),
        "onSecondaryContainer on secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiary on tertiary" to (s.onTertiary to s.tertiary),
        "onTertiaryContainer on tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "onError on error" to (s.onError to s.error),
        "onErrorContainer on errorContainer" to (s.onErrorContainer to s.errorContainer),
        "onSurface on surface" to (s.onSurface to s.surface),
        "onSurface on surfaceContainerLowest" to (s.onSurface to s.surfaceContainerLowest),
        "onSurface on surfaceContainerLow" to (s.onSurface to s.surfaceContainerLow),
        "onSurface on surfaceContainer" to (s.onSurface to s.surfaceContainer),
        "onSurface on surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
        "onSurface on surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "onSurfaceVariant on surface" to (s.onSurfaceVariant to s.surface),
        "onSurfaceVariant on surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
        "inverseOnSurface on inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
    )

    @Test
    fun everyPairOfTextAndGroundReadsInBothModes() {
        for (dark in modes) {
            for ((name, pair) in textPairs(AppTheme.accentScheme(AccentColor.Pure, dark))) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5, "${if (dark) "dark" else "light"} $name is ${"%.2f".format(ratio)}:1, needs 4.5")
            }
        }
    }

    @Test
    fun controlsAndOutlinesAreVisibleAgainstThePage() {
        for (dark in modes) {
            val s = AppTheme.accentScheme(AccentColor.Pure, dark)
            val mode = if (dark) "dark" else "light"
            assertTrue(contrast(s.primary, s.surface) >= 3.0, "$mode primary on surface")
            assertTrue(contrast(s.outline, s.surface) >= 3.0, "$mode outline on surface")
        }
    }

    @Test
    fun surfacesStepFromThePage_soACardStillStandsOffIt() {
        val dark = AppTheme.accentScheme(AccentColor.Pure, isDark = true)
        assertTrue(dark.isDark, "dark surface")
        val darkSteps = listOf(
            dark.surfaceContainerLowest, dark.surfaceContainerLow, dark.surfaceContainer,
            dark.surfaceContainerHigh, dark.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(darkSteps.sorted(), darkSteps, "dark: each container tier lifts above the one before")
        assertEquals(darkSteps.distinct(), darkSteps, "dark: no two tiers are the same grey")

        val light = AppTheme.accentScheme(AccentColor.Pure, isDark = false)
        assertTrue(!light.isDark, "light surface")
        val lightSteps = listOf(
            light.surfaceContainerLowest, light.surfaceContainerLow, light.surfaceContainer,
            light.surfaceContainerHigh, light.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(lightSteps.sortedDescending(), lightSteps, "light: each container tier sinks below the one before")
        assertEquals(lightSteps.distinct(), lightSteps, "light: no two tiers are the same grey")
    }

    @Test
    fun theSwatchIsBlackOrWhiteWithTwoGreysBeside() {
        for (dark in modes) {
            val s = AppTheme.paletteSwatch(AppTheme.accentScheme(AccentColor.Pure, dark))
            assertEquals(if (dark) white else black, s.primary)
            assertNotEquals(s.primary, s.container)
            assertNotEquals(s.container, s.tertiary)
            assertNotEquals(s.primary, s.tertiary)
        }
    }

    @Test
    fun itFollowsTheThemeChoice_lightDarkOrTheSystems() {
        var seen: ColorScheme? = null
        var mode by mutableStateOf(ThemeMode.Dark)
        compose.setContent {
            NormPixTheme(Appearance(mode, ColorSource.Custom, AccentColor.Pure)) { seen = MaterialTheme.colorScheme }
        }
        for (picked in listOf(ThemeMode.Dark, ThemeMode.Light)) {
            mode = picked
            compose.waitForIdle()
            val dark = picked == ThemeMode.Dark
            assertEquals(if (dark) black else white, seen?.surface, "$picked surface")
            assertEquals(if (dark) white else black, seen?.onSurface, "$picked on surface")
        }
        // Follow system: Robolectric's default configuration is a light one.
        mode = ThemeMode.System
        compose.waitForIdle()
        assertEquals(white, seen?.surface, "the system's light")
    }
}
