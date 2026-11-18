package com.example.normpix.quickadd

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performSemanticsAction
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.testing.OverlayHarness
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.backdropBlurRadius
import com.example.normpix.ui.theme.AppFrostedTokens
import kotlin.math.roundToInt
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app behind the quick add overlay is blurred as a NormPix page is under a sheet (owner, 2026-11-18): the window's blur-behind
 * follows the sheet - the page's full radius while it is up, nothing once it has gone - and stays off where the page would not be
 * blurred (Background blur off, or the phone not blurring).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class QuickAddBlurTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val env = TestEnvironment(prefsName = "quick_add_blur_test")

    @After
    fun tearDown() = env.close()

    private fun shown(text: String) = compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun blur(): Int {
        val attrs = compose.activity.window.attributes
        return if (attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0) attrs.blurBehindRadius else 0
    }

    private val fullRadius get() = with(compose.density) { AppFrostedTokens.blurRadius.toPx() }.roundToInt()

    private fun dismissSheet() =
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)

    @Test
    fun `the app behind is blurred while the sheet is up, by the page's radius, and not once it has gone`() {
        val overlay = OverlayHarness(compose, env, windowBlur = true)
        assertEquals(0, blur(), "nothing before the overlay asks")
        overlay.add(TransactionType.Expense)
        compose.waitUntil(10_000) { shown("New expense") }
        compose.waitUntil(5_000) { blur() == fullRadius }
        assertTrue(fullRadius > 0)
        val up = compose.activity.window.attributes
        assertTrue(up.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0, "a dim goes with the blur, so a phone draws its layer")
        assertEquals(DIM_WITH_BLUR, up.dimAmount, "one nobody sees")

        dismissSheet()
        compose.waitUntil(10_000) { !shown("New expense") }
        compose.waitUntil(5_000) { blur() == 0 }
        val attrs = compose.activity.window.attributes
        assertEquals(0, attrs.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND, "the flag goes with the blur")
        assertEquals(0, attrs.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND, "and so does the dim")
    }

    @Test
    fun `with Background blur off, or a phone that is not blurring, nothing is blurred`() {
        env.container.preferences.setSheetBlurEnabled(false)
        val off = OverlayHarness(compose, env, windowBlur = true)
        off.add(TransactionType.Expense)
        compose.waitUntil(10_000) { shown("New expense") }
        compose.waitForIdle()
        assertEquals(0, blur(), "Background blur is off")
        assertEquals(0, compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND, "no window dim either: the sheet's scrim dims")
    }

    @Test
    fun `a phone that is not blurring has nothing blurred`() {
        val noSystemBlur = OverlayHarness(compose, env, windowBlur = false)
        noSystemBlur.add(TransactionType.Expense)
        compose.waitUntil(10_000) { shown("New expense") }
        compose.waitForIdle()
        assertEquals(0, blur())
    }

    @Test
    fun `the overlay and the page share one blur curve`() {
        assertEquals(0f, backdropBlurRadius(0f, 100f))
        assertEquals(100f, backdropBlurRadius(1f, 100f))
        assertEquals(75f, backdropBlurRadius(0.5f, 100f), "front-loaded: half the sheet up is three-quarters of the blur")
        assertEquals(100f, backdropBlurRadius(1.4f, 100f), "never past the full radius")
    }
}
