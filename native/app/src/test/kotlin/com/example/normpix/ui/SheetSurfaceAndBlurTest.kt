package com.example.normpix.ui

import androidx.activity.ComponentActivity
import org.robolectric.shadows.ShadowDialog
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.core.graphics.Insets
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.DialogBackdropBlur
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalWindowBlurOverride
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.components.SheetShape
import com.example.normpix.ui.components.rememberDialogSurface
import com.example.normpix.ui.components.rememberSheetSurface
import com.example.normpix.ui.components.sheetShareOnScreen
import com.example.normpix.ui.theme.AppFrostedTokens
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.NormPixTheme
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
 * Sheets and dialogs lie over a page blurred by as much of the sheet as is on screen - so the blur rises with the sheet,
 * follows a drag and is gone when it is - and the blurred page shows through them, as in the launcher's app drawer.
 * Where nothing is blurred they are solid.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class SheetSurfaceAndBlurTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tokens() {
        // CSS blur(24px) is sigma 24; an Android blur radius is about sqrt(3) x sigma.
        assertEquals(40.dp, AppFrostedTokens.blurRadius)
        // Enough of the blurred page shows through to read as colour, not enough for it to compete with the text.
        assertTrue(AppFrostedTokens.surfaceAlpha in 0.55f..0.75f, "surface ${AppFrostedTokens.surfaceAlpha}")
        assertTrue(AppFrostedTokens.scrimAlpha <= 0.12f, "the blur, not a dim, sets the page back")
        assertEquals(AppRadius.xxl, AppFrostedTokens.cornerRadius)
        assertEquals(AppFrostedTokens.cornerRadius, SheetShape.topStart.toPx(Size(100f, 100f), Density(1f)).dp)
        assertEquals(0f, SheetShape.bottomStart.toPx(Size(100f, 100f), Density(1f)), "square where it meets the screen's edge")
    }

    // --- the surfaces -----------------------------------------------------------------------------------------

    private class Surfaces(
        val sheetAlpha: Float,
        val sheetBlur: Boolean,
        val scrimAlpha: Float,
        val dialogAlpha: Float,
        val dialogBlur: Boolean,
    )

    private fun surfaces(enabled: Boolean, blurAvailable: Boolean): Surfaces {
        var result: Surfaces? = null
        compose.setContent {
            NormPixTheme {
                val sheet = rememberSheetSurface(enabled, blurAvailable)
                val dialog = rememberDialogSurface(enabled, blurAvailable)
                result = Surfaces(sheet.container.alpha, sheet.blurBehind, sheet.scrim.alpha, dialog.container.alpha, dialog.blurBehind)
            }
        }
        compose.waitForIdle()
        return result!!
    }

    @Test
    fun withBlurOn_theBlurredPageShowsThroughSheetsAndDialogs() {
        val s = surfaces(enabled = true, blurAvailable = true)
        assertTrue(s.sheetBlur)
        assertTrue(s.dialogBlur)
        assertEquals(AppFrostedTokens.surfaceAlpha, s.sheetAlpha, 0.01f)
        assertEquals(AppFrostedTokens.surfaceAlpha, s.dialogAlpha, 0.01f)
        assertEquals(AppFrostedTokens.scrimAlpha, s.scrimAlpha, 0.01f)
    }

    @Test
    fun withBlurSwitchedOff_nothingIsBlurred() {
        val s = surfaces(enabled = false, blurAvailable = true)
        assertFalse(s.sheetBlur)
        assertFalse(s.dialogBlur)
        // See-through over a sharp page would be unreadable.
        assertEquals(1f, s.sheetAlpha)
        assertEquals(1f, s.dialogAlpha)
        assertTrue(s.scrimAlpha > 0.2f, "Material's usual dim instead")
    }

    @Test
    fun whereTheSystemDoesNotBlur_nothingIsBlurred() {
        // Battery saver, the developer option, or a device that cannot.
        val s = surfaces(enabled = true, blurAvailable = false)
        assertFalse(s.sheetBlur)
        assertFalse(s.dialogBlur)
        assertEquals(1f, s.sheetAlpha)
        assertEquals(1f, s.dialogAlpha)
    }

    @Test
    fun sheetSurface_givesUnstyledContentTheThemesOnSurfaceColour() {
        var content = Color.Unspecified
        var onSurface = Color.Unspecified
        compose.setContent {
            NormPixTheme {
                content = rememberSheetSurface(enabled = true).content
                onSurface = MaterialTheme.colorScheme.onSurface
            }
        }
        compose.waitForIdle()
        assertEquals(onSurface, content)
    }

    // --- how much of a sheet is up ---------------------------------------------------------------------------

    @Test
    fun theShareFollowsTheSheetUpTheScreen() {
        val window = 1000f
        assertEquals(0f, sheetShareOnScreen(window, top = 1000f, height = 300f), "still below the edge")
        assertEquals(0f, sheetShareOnScreen(window, top = 1200f, height = 300f))
        assertEquals(0.5f, sheetShareOnScreen(window, top = 850f, height = 300f), 0.001f, "half way up")
        assertEquals(1f, sheetShareOnScreen(window, top = 700f, height = 300f), "all the way up")
        assertEquals(0f, sheetShareOnScreen(window, top = 500f, height = 0f), "nothing laid out yet")
    }

    @Test
    fun aTallSheetResting_halfOpen_hasThePageFullyBlurred() {
        val window = 1000f
        // An expandable sheet rests with half the window showing, less its handle.
        assertEquals(1f, sheetShareOnScreen(window, top = 550f, height = 900f))
        assertEquals(0.5f, sheetShareOnScreen(window, top = 800f, height = 900f), 0.001f)
    }

    @Test
    fun thePageIsBlurredByTheMostOpenOverlay_andClearedWithIt() {
        val overlays = AppOverlays()
        assertEquals(0f, overlays.backdropBlur)
        val sheet = Any()
        val dialog = Any()
        overlays.setBackdropBlur(sheet, 0.3f)
        overlays.setBackdropBlur(dialog, 0.8f)
        assertEquals(0.8f, overlays.backdropBlur)
        overlays.setBackdropBlur(dialog, 4f)
        assertEquals(1f, overlays.backdropBlur, "never more than the full radius")
        overlays.clearBackdropBlur(dialog)
        assertEquals(0.3f, overlays.backdropBlur)
        overlays.clearBackdropBlur(sheet)
        assertEquals(0f, overlays.backdropBlur)
    }

    // --- a real sheet ----------------------------------------------------------------------------------------

    private fun host(overlays: AppOverlays, blurAvailable: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalWindowBlurOverride provides blurAvailable) {
                NormPixTheme { OverlayHost(overlays) }
            }
        }
        compose.waitForIdle()
    }

    private fun open(overlays: AppOverlays, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch { overlays.sheet<Unit> { content() } }
        }
    }

    @Test
    fun anOpenSheetBlursThePage_andClosingItClearsTheBlur() {
        val overlays = AppOverlays()
        host(overlays, blurAvailable = true)
        open(overlays) { Text("Sheet body") }
        compose.waitForIdle()
        compose.onNodeWithText("Sheet body").assertIsDisplayed()
        assertEquals(1f, overlays.backdropBlur, 0.01f)

        compose.runOnUiThread { overlays.dismissAll() }
        compose.waitForIdle()
        assertEquals(0f, overlays.backdropBlur)
    }

    @Test
    fun theBlurRisesWithTheSheet_notAfterIt() {
        val overlays = AppOverlays()
        host(overlays, blurAvailable = true)
        compose.mainClock.autoAdvance = false
        open(overlays) { Text("Sheet body") }
        val seen = mutableListOf<Float>()
        repeat(60) {
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { seen += overlays.backdropBlur }
        }
        compose.mainClock.autoAdvance = true
        // Partway values while the sheet slides up - not 0 until it lands and then all at once.
        assertTrue(seen.any { it in 0.05f..0.95f }, "the blur passes through partial strengths as the sheet rises: $seen")
        assertTrue(seen.zipWithNext().all { (a, b) -> b >= a - 0.001f }, "and only ever rises while it does: $seen")
        assertEquals(1f, seen.last(), 0.01f)
    }

    @Test
    fun atFullHeight_aSheetStopsUnderTheStatusBar() {
        val overlays = AppOverlays()
        host(overlays, blurAvailable = true)
        // Taller than the screen, so it opens all the way up.
        open(overlays) { Box(Modifier.fillMaxWidth().height(2000.dp)) { Text("Tall body") } }
        compose.waitForIdle()
        val handle = compose.onNode(hasContentDescription("Drag handle", substring = true))
        val before = handle.getUnclippedBoundsInRoot().top
        assertTrue(before.value < 1f, "no status bar yet: the sheet reaches the top ($before)")

        val statusBar = 40 // px; the test screen is 1x, so also dp
        compose.runOnUiThread {
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBar, 0, 0))
                .setVisible(WindowInsetsCompat.Type.statusBars(), true)
                .build()
            ViewCompat.dispatchApplyWindowInsets(ShadowDialog.getLatestDialog().window!!.decorView, insets)
        }
        compose.waitForIdle()
        val top = handle.getUnclippedBoundsInRoot().top
        assertEquals(statusBar.toFloat(), top.value, 1f, "the sheet's top edge sits just under the status bar")
    }

    /** A centred dialog over a page, blurring it as [DialogBackdropBlur] does (the app's pickers are sheets now, not dialogs). */
    private fun showDialog(overlays: AppOverlays, blurAvailable: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalWindowBlurOverride provides blurAvailable, LocalOverlays provides overlays) {
                NormPixTheme {
                    val surface = rememberDialogSurface(enabled = true)
                    AlertDialog(
                        onDismissRequest = {},
                        confirmButton = {},
                        containerColor = surface.container,
                        text = {
                            DialogBackdropBlur(surface.blurBehind)
                            Text("A dialog")
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun aDialogBlursThePage_underASheetsLightDimNotTheWindowsDarkOne() {
        val overlays = AppOverlays()
        showDialog(overlays, blurAvailable = true)
        assertEquals(1f, overlays.backdropBlur, 0.01f)
        assertEquals(AppFrostedTokens.scrimAlpha, ShadowDialog.getLatestDialog().window!!.attributes.dimAmount, 0.01f)
    }

    @Test
    fun whereTheSystemDoesNotBlur_aDialogKeepsTheUsualDim() {
        val overlays = AppOverlays()
        showDialog(overlays, blurAvailable = false)
        assertEquals(0f, overlays.backdropBlur)
        assertTrue(ShadowDialog.getLatestDialog().window!!.attributes.dimAmount > 0.3f)
    }

    @Test
    fun whereTheSystemDoesNotBlur_aSheetLeavesThePageAlone() {
        val overlays = AppOverlays()
        host(overlays, blurAvailable = false)
        open(overlays) { Text("Sheet body") }
        compose.waitForIdle()
        compose.onNodeWithText("Sheet body").assertIsDisplayed()
        assertEquals(0f, overlays.backdropBlur)
    }

    @Test
    fun sheet_unstyledTextIsOnSurfaceNotBlack() {
        val overlays = AppOverlays()
        var inside = Color.Unspecified
        var onSurface = Color.Unspecified
        compose.setContent {
            NormPixTheme {
                onSurface = MaterialTheme.colorScheme.onSurface
                OverlayHost(overlays)
            }
        }
        compose.waitForIdle()
        open(overlays) {
            inside = LocalContentColor.current
            Text("Plain")
        }
        compose.waitForIdle()
        assertEquals(onSurface, inside)
    }

    @Test
    fun modalBottomSheet_rendersContentAndDragHandle() {
        val overlays = AppOverlays()
        host(overlays, blurAvailable = true)
        open(overlays) {
            Text("Sheet Header")
            Button(onClick = {}) { Text("Action Button") }
        }
        compose.waitForIdle()
        compose.onNodeWithText("Sheet Header").assertIsDisplayed()
        compose.onNodeWithText("Action Button").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasContentDescription("Drag handle", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }
}
