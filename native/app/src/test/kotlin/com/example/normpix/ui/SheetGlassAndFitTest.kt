package com.example.normpix.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LocalWindowBlurOverride
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.components.SeeThroughColors
import com.example.normpix.ui.components.rememberDialogSurface
import com.example.normpix.ui.components.seeThroughOver
import com.example.normpix.ui.components.sheetFits
import com.example.normpix.ui.components.solidColorScheme
import com.example.normpix.ui.theme.AppFrostedTokens
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Inside a see-through sheet the fields, tracks and cards are see-through too - tints that come out as their usual tones
 * over the sheet - while menus and dialogs opened from it stay surfaces of their own. And a tall sheet opens all the way
 * when it fits, half way only when it does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SheetGlassAndFitTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // --- the tints ---------------------------------------------------------------------------------------------

    private fun ColorScheme.levels() = listOf(
        "surface" to surface,
        "surfaceContainerLowest" to surfaceContainerLowest,
        "surfaceContainerLow" to surfaceContainerLow,
        "surfaceContainer" to surfaceContainer,
        "surfaceContainerHigh" to surfaceContainerHigh,
        "surfaceContainerHighest" to surfaceContainerHighest,
    )

    private fun assertTintsComeOutAsTheTones(scheme: ColorScheme) {
        val ground = scheme.surfaceContainerLow
        val tinted = scheme.seeThroughOver(ground)
        for ((solid, tint) in scheme.levels().zip(tinted.levels())) {
            val (name, tone) = solid
            val out = tint.second.compositeOver(ground)
            val off = maxOf(abs(out.red - tone.red), abs(out.green - tone.green), abs(out.blue - tone.blue))
            assertTrue(off < 0.02f, "$name over the sheet comes out as its tone: $tone vs $out")
            assertTrue(tint.second.alpha <= 0.6f, "$name is see-through: ${tint.second}")
        }
        assertEquals(0f, tinted.surfaceContainerLow.alpha, "the sheet's own tone is clear")
    }

    @Test
    fun overASolidSheetTheTintsLookExactlyAsBefore_dark() = assertTintsComeOutAsTheTones(darkColorScheme())

    @Test
    fun overASolidSheetTheTintsLookExactlyAsBefore_light() = assertTintsComeOutAsTheTones(lightColorScheme())

    @Test
    fun overASolidSheetTheTintsLookExactlyAsBefore_appTheme() {
        var dark: ColorScheme? = null
        var light: ColorScheme? = null
        compose.setContent {
            NormPixTheme(com.example.normpix.ui.theme.Appearance(mode = com.example.normpix.settings.ThemeMode.Dark)) { dark = MaterialTheme.colorScheme }
            NormPixTheme(com.example.normpix.ui.theme.Appearance(mode = com.example.normpix.settings.ThemeMode.Light)) { light = MaterialTheme.colorScheme }
        }
        compose.waitForIdle()
        assertTintsComeOutAsTheTones(dark!!)
        assertTintsComeOutAsTheTones(light!!)
    }

    // --- inside a sheet ----------------------------------------------------------------------------------------

    private class Seen(val high: Color, val contentOnHigh: Color, val onSurface: Color, val menu: Color, val dialog: Color, val solidHigh: Color)

    private fun insideASheet(blurAvailable: Boolean): Seen {
        val overlays = AppOverlays()
        var seen: Seen? = null
        compose.setContent {
            CompositionLocalProvider(LocalWindowBlurOverride provides blurAvailable) {
                NormPixTheme { OverlayHost(overlays) }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                overlays.sheet<Unit> {
                    val scheme = MaterialTheme.colorScheme
                    seen = Seen(
                        high = scheme.surfaceContainerHigh,
                        contentOnHigh = contentColorFor(scheme.surfaceContainerHigh),
                        onSurface = scheme.onSurface,
                        menu = solidColorScheme.surfaceContainer,
                        dialog = rememberDialogSurface(enabled = true, blurAvailable = blurAvailable).container,
                        solidHigh = solidColorScheme.surfaceContainerHigh,
                    )
                    Text("Body")
                }
            }
        }
        compose.waitForIdle()
        return seen!!
    }

    @Test
    fun inASeeThroughSheet_fieldsAreSeeThrough_andMenusAndDialogsAreNot() {
        val s = insideASheet(blurAvailable = true)
        assertTrue(s.high.alpha < 0.3f, "a field's fill lets the blurred page through: ${s.high}")
        assertEquals(s.onSurface, s.contentOnHigh, "and its text still reads as on a surface")
        assertEquals(1f, s.menu.alpha, "a menu pops up over the sheet's sharp content: solid")
        assertEquals(s.solidHigh.copy(alpha = AppFrostedTokens.surfaceAlpha), s.dialog, "a dialog is a surface of its own")
    }

    @Test
    fun inASolidSheet_nothingChanges() {
        val s = insideASheet(blurAvailable = false)
        assertEquals(1f, s.high.alpha)
        assertEquals(s.solidHigh, s.high)
        assertEquals(s.solidHigh, s.dialog)
    }

    @Test
    fun seeThroughColorsNest_withoutTintingTwice() {
        var outer = Color.Unspecified
        var inner = Color.Unspecified
        compose.setContent {
            NormPixTheme {
                val ground = MaterialTheme.colorScheme.surfaceContainerLow
                SeeThroughColors(true, ground) {
                    outer = MaterialTheme.colorScheme.surfaceContainerHigh
                    SeeThroughColors(true, ground) { inner = MaterialTheme.colorScheme.surfaceContainerHigh }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(outer, inner)
    }

    // --- opening as far as it fits -----------------------------------------------------------------------------

    @Test
    fun theFitRule() {
        assertTrue(sheetFits(windowHeight = 2400, top = 100, bottom = 0, sheetHeight = 1800))
        assertFalse(sheetFits(windowHeight = 2400, top = 100, bottom = 0, sheetHeight = 2300), "held to the space there is")
        assertFalse(sheetFits(windowHeight = 2400, top = 100, bottom = 900, sheetHeight = 1800), "the keyboard is up")
    }

    private fun openExpandable(content: @Composable () -> Unit): Float {
        val overlays = AppOverlays()
        compose.setContent { NormPixTheme { OverlayHost(overlays) } }
        compose.waitForIdle()
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch { overlays.sheet<Unit>(expandable = true) { content() } }
        }
        compose.waitForIdle()
        return compose.onNode(hasContentDescription("Drag handle", substring = true)).getUnclippedBoundsInRoot().top.value
    }

    @Test
    fun aTallSheetThatFits_opensAllTheWay() {
        // 600dp of content on an 891dp screen: more than half, so Material would stop it half way.
        val top = openExpandable { Box(Modifier.fillMaxWidth().height(600.dp)) }
        val handle = 48f
        assertEquals(891f - 600f - handle, top, 2f, "the whole sheet is up")
    }

    @Test
    fun aSheetTallerThanTheScreen_opensHalfWay() {
        val top = openExpandable {
            Column(Modifier.verticalScroll(rememberScrollState())) { Box(Modifier.fillMaxWidth().height(2000.dp)) }
        }
        assertEquals(891f / 2, top, 2f, "half way, to be dragged up")
    }
}
