package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.TimePickerDialogFor
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalWindowBlurOverride
import com.example.normpix.ui.components.OverlayHost
import com.example.normpix.ui.theme.NormPixTheme
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A sheet or picker opened over a sheet blurs that sheet, as the page under them all is blurred - so the form underneath
 * shows through as soft colour, never as sharp text behind the one on top.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class StackedOverlayBlurTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun eachOverlayIsBlurredByWhatOpenedOverIt() {
        val overlays = AppOverlays()
        val form = Any()
        val confirm = Any()
        overlays.setBackdropBlur(form, 1f)
        overlays.setBackdropBlur(confirm, 0.4f)
        assertEquals(0.4f, overlays.blurAbove(form), "the form, under the confirmation")
        assertEquals(0f, overlays.blurAbove(confirm), "nothing over the confirmation")
        overlays.setBackdropBlur(form, 0.9f)
        assertEquals(0f, overlays.blurAbove(confirm), "a later report from the form does not move it to the top")
        overlays.clearBackdropBlur(confirm)
        assertEquals(0f, overlays.blurAbove(form), "the confirmation gone, the form is sharp")
        assertEquals(0f, overlays.blurAbove(Any()), "an overlay not blurring anything")
    }

    private val overlays = AppOverlays()
    private var picking by mutableStateOf(false)
    private var systemBlurs by mutableStateOf(true)
    private var fieldFill = Color.Unspecified

    /** A sheet with a hard red | blue seam across it, under [OverlayHost]. */
    private fun openForm(blurAvailable: Boolean) {
        systemBlurs = blurAvailable
        compose.setContent {
            CompositionLocalProvider(LocalWindowBlurOverride provides systemBlurs, LocalOverlays provides overlays) {
                NormPixTheme { OverlayHost(overlays) }
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch {
                overlays.sheet<Unit> {
                    fieldFill = MaterialTheme.colorScheme.surfaceContainerHigh
                    Row(Modifier.fillMaxWidth().height(200.dp).testTag("form")) {
                        Box(Modifier.weight(1f).height(200.dp).background(Color.Red))
                        Box(Modifier.weight(1f).height(200.dp).background(Color.Blue))
                    }
                    if (picking) TimePickerDialogFor(LocalDateTime.of(2026, 10, 8, 9, 30), { _, _ -> }, { picking = false })
                }
            }
        }
        compose.waitForIdle()
    }

    private val formRequest get() = overlays.sheets.single()

    /** Blue bled 8dp left of the form's seam: 0 while the form is sharp. */
    private fun bleed(): Float {
        val px = compose.onNodeWithTag("form").captureToImage().toPixelMap()
        return px[px.width / 2 - 8, px.height / 2].blue
    }

    private fun confirmOver() {
        compose.runOnUiThread {
            CoroutineScope(Dispatchers.Main.immediate).launch { overlays.confirm(title = "Discard this entry?", confirmLabel = "Discard") }
        }
        compose.waitForIdle()
    }

    @Test
    fun aConfirmationOverAFormBlursTheForm() {
        openForm(blurAvailable = true)
        assertEquals(0f, overlays.blurAbove(formRequest))
        assertEquals(0f, bleed(), 0.02f, "sharp on its own")

        confirmOver()
        assertEquals(1f, overlays.blurAbove(formRequest), 0.01f)
        assertTrue(bleed() > 0.1f, "blurred under the confirmation: ${bleed()}")

        compose.runOnUiThread { overlays.dialogs.single().result.complete(false) }
        compose.waitForIdle()
        assertEquals(0f, overlays.blurAbove(formRequest))
        assertEquals(0f, bleed(), 0.02f, "sharp again once it is answered")
    }

    @Test
    fun aPickerOpenedFromAFormBlursTheForm() {
        openForm(blurAvailable = true)
        picking = true
        compose.waitForIdle()
        assertEquals(1f, overlays.blurAbove(formRequest), 0.01f)
        assertTrue(bleed() > 0.1f, "blurred under the time picker: ${bleed()}")

        picking = false
        compose.waitForIdle()
        assertEquals(0f, overlays.blurAbove(formRequest))
        assertEquals(0f, bleed(), 0.02f)
    }

    @Test
    fun whenTheSystemStopsBlurring_anOpenSheetFollowsAtOnce() {
        // Battery saver, the developer option, or Accessibility > Reduce blur effects (Android 16 QPR2+), switched on
        // while a form is open under a confirmation.
        openForm(blurAvailable = true)
        confirmOver()
        assertEquals(1f, overlays.backdropBlur, 0.01f)
        assertTrue(fieldFill.alpha < 0.3f)

        systemBlurs = false
        compose.waitForIdle()
        assertEquals(0f, overlays.backdropBlur, "the page is sharp")
        assertEquals(0f, overlays.blurAbove(formRequest))
        assertEquals(0f, bleed(), 0.02f, "so is the form")
        assertEquals(1f, fieldFill.alpha, "and its fields are solid again, as the sheets are")

        systemBlurs = true
        compose.waitForIdle()
        assertEquals(1f, overlays.backdropBlur, 0.01f, "and back when it blurs again")
        assertTrue(fieldFill.alpha < 0.3f)
    }

    @Test
    fun whereTheSystemDoesNotBlur_theFormUnderneathStaysAsItIs() {
        openForm(blurAvailable = false)
        confirmOver()
        assertEquals(0f, overlays.blurAbove(formRequest))
        assertEquals(0f, bleed(), 0.02f)
    }
}
