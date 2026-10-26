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
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import kotlin.math.abs
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
 * Volt, the lime-on-near-black accent: its colours are the reference's (lime and dark green), every pair of text and
 * ground reads in both modes, the surfaces step the right way, and the status colours that are drawn from the primary
 * (success, warning, info) still tell one another apart.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VoltThemeTest {
    @get:Rule
    val compose = createComposeRule()

    private val lime = Color(0xFFAFE22D)
    private val ink = Color(0xFF0A2C1B)

    private fun contrast(a: Color, b: Color): Double {
        val hi = max(a.luminance(), b.luminance()).toDouble()
        val lo = min(a.luminance(), b.luminance()).toDouble()
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Hue in degrees (HSV), the plain colour-wheel angle. */
    private fun hue(c: Color): Double {
        val max = max(c.red, max(c.green, c.blue))
        val min = min(c.red, min(c.green, c.blue))
        if (max == min) return 0.0
        val d = (max - min).toDouble()
        val h = when (max) {
            c.red -> ((c.green - c.blue) / d) % 6
            c.green -> (c.blue - c.red) / d + 2
            else -> (c.red - c.green) / d + 4
        }
        return (h * 60 + 360) % 360
    }

    private fun hueApart(a: Color, b: Color): Double {
        val d = abs(hue(a) - hue(b))
        return min(d, 360 - d)
    }

    private val modes = listOf(false, true)

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
    fun theColoursAreTheReferencesLimeAndInk() {
        assertEquals(lime, AccentColor.Volt.seed)
        assertEquals("Volt", AccentColor.Volt.label)

        val dark = AppTheme.accentScheme(AccentColor.Volt, isDark = true)
        assertEquals(lime, dark.primary, "dark: lime is the colour of controls")
        assertEquals(ink, dark.onPrimary, "dark: ink on lime")

        val light = AppTheme.accentScheme(AccentColor.Volt, isDark = false)
        assertEquals(ink, light.primary, "light: ink is the colour of controls")
        assertEquals(lime, light.onPrimary, "light: lime on ink")
        assertEquals(lime, light.primaryContainer, "light: lime fills the containers")
        assertEquals(ink, light.onPrimaryContainer)
    }

    @Test
    fun everyPairOfTextAndGroundReadsInBothModes() {
        for (dark in modes) {
            for ((name, pair) in textPairs(AppTheme.accentScheme(AccentColor.Volt, dark))) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5, "${if (dark) "dark" else "light"} $name is ${"%.2f".format(ratio)}:1, needs 4.5")
            }
        }
    }

    @Test
    fun controlsAndOutlinesAreVisibleAgainstThePage() {
        for (dark in modes) {
            val s = AppTheme.accentScheme(AccentColor.Volt, dark)
            val mode = if (dark) "dark" else "light"
            assertTrue(contrast(s.primary, s.surface) >= 3.0, "$mode primary on surface")
            assertTrue(contrast(s.outline, s.surface) >= 3.0, "$mode outline on surface")
        }
    }

    @Test
    fun surfacesAreDarkInDarkAndLightInLight_andStepTheRightWay() {
        val dark = AppTheme.accentScheme(AccentColor.Volt, isDark = true)
        assertTrue(dark.isDark, "dark surface")
        val darkSteps = listOf(
            dark.surfaceContainerLowest, dark.surfaceContainerLow, dark.surfaceContainer,
            dark.surfaceContainerHigh, dark.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(darkSteps.sorted(), darkSteps, "dark: each container tier lifts above the one before")
        assertEquals(darkSteps.distinct(), darkSteps, "dark: no two tiers are the same colour")

        val light = AppTheme.accentScheme(AccentColor.Volt, isDark = false)
        assertTrue(!light.isDark, "light surface")
        val lightSteps = listOf(
            light.surfaceContainerLowest, light.surfaceContainerLow, light.surfaceContainer,
            light.surfaceContainerHigh, light.surfaceContainerHighest,
        ).map { it.luminance() }
        assertEquals(lightSteps.sortedDescending(), lightSteps, "light: each container tier sinks below the one before")
        assertEquals(lightSteps.distinct(), lightSteps, "light: no two tiers are the same colour")
    }

    @Test
    fun theStatusColoursStillTellOneAnotherApart() {
        for (dark in modes) {
            val scheme = AppTheme.accentScheme(AccentColor.Volt, dark)
            val status = AppStatusColors.fromScheme(scheme, dark)
            val mode = if (dark) "dark" else "light"
            val tones = mapOf(
                "success" to status.success.color,
                "warning" to status.warning.color,
                "info" to status.info.color,
                "error" to scheme.error,
            )
            val names = tones.keys.toList()
            for (i in names.indices) for (j in i + 1 until names.size) {
                val apart = hueApart(tones.getValue(names[i]), tones.getValue(names[j]))
                assertTrue(apart >= 25.0, "$mode ${names[i]} and ${names[j]} are only ${"%.0f".format(apart)} degrees apart")
            }
            for ((name, role) in mapOf("success" to status.success, "warning" to status.warning, "info" to status.info)) {
                assertTrue(contrast(role.onColor, role.color) >= 4.5, "$mode $name: its text on its colour")
                assertTrue(contrast(role.onContainer, role.container) >= 4.5, "$mode $name: its text on its container")
            }
        }
    }

    @Test
    fun theSwatchShowsTheInkAndTheLime_andEveryAccentsOwnThree() {
        for (dark in modes) {
            val volt = AppTheme.paletteSwatch(AppTheme.accentScheme(AccentColor.Volt, dark))
            // Light is ink controls on lime fills; dark the lime itself on dark green fills: the pair is in the swatch either way.
            assertEquals(if (dark) lime else ink, volt.primary, "${if (dark) "dark" else "light"} Volt primary")
            assertEquals(if (dark) Color(0xFF1C4A30) else lime, volt.container, "${if (dark) "dark" else "light"} Volt container")
            for (accent in AccentColor.entries) {
                val scheme = AppTheme.accentScheme(accent, dark)
                val swatch = AppTheme.paletteSwatch(scheme)
                assertEquals(scheme.primary, swatch.primary, accent.name)
                assertEquals(scheme.primaryContainer, swatch.container, accent.name)
                assertEquals(scheme.tertiary, swatch.tertiary, accent.name)
                assertNotEquals(swatch.primary, swatch.container, "${accent.name} shows its primary and its container as two colors")
            }
        }
    }

    @Test
    fun theOtherAccentsAreStillGeneratedFromTheirSeeds() {
        for (accent in AccentColor.entries - AccentColor.Volt - AccentColor.Pure) {
            for (dark in modes) {
                val expected = dynamicColorScheme(seedColor = accent.seed, isDark = dark, style = PaletteStyle.Fidelity)
                assertEquals(expected.primary, AppTheme.accentScheme(accent, dark).primary, "${accent.name} ${if (dark) "dark" else "light"}")
                assertNotEquals(AppTheme.voltScheme(dark).primary, AppTheme.accentScheme(accent, dark).primary, accent.name)
            }
        }
    }

    @Test
    fun theAppIsDrawnInVoltWhenItIsPicked() {
        var seen: ColorScheme? = null
        var mode by mutableStateOf(ThemeMode.Dark)
        compose.setContent {
            NormPixTheme(Appearance(mode, ColorSource.Custom, AccentColor.Volt)) { seen = MaterialTheme.colorScheme }
        }
        for ((picked, dark) in listOf(ThemeMode.Dark to true, ThemeMode.Light to false)) {
            mode = picked
            compose.waitForIdle()
            val expected = AppTheme.voltScheme(dark)
            val name = if (dark) "dark" else "light"
            // A ColorScheme has no equality of its own; the roles the app leans on most stand for it.
            assertEquals(expected.primary, seen?.primary, "$name primary")
            assertEquals(expected.primaryContainer, seen?.primaryContainer, "$name primary container")
            assertEquals(expected.surface, seen?.surface, "$name surface")
            assertEquals(expected.onSurface, seen?.onSurface, "$name on surface")
        }
    }
}
