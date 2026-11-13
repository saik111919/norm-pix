package com.example.normpix.navigation

import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.example.normpix.settings.BackGestureSpeed
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The back gesture over a pushed page, in the app's own navigation host ([AppNavHost]): the page on top (red) closes the way the
 * Back gesture style closes a page, by how far the finger has gone, over the page beneath (blue) - as a tab does
 * ([TabBackPreviewTest]). Before 2026-11-11 the host was not given the gesture's transitions and played the library's default, a
 * card shrunk to 0.7 of the screen: every "red at the screen's edge" below was blue then.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w100dp-h100dp-xxhdpi")
class PageBackGestureTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    /** A blue page with a red page opened over it, in the page [style]; the back gesture closes it as [backStyle] does. */
    private fun show(style: PageAnimationType, backStyle: PageAnimationType = style) {
        compose.setContent {
            CompositionLocalProvider(
                LocalPageAnimation provides style,
                LocalBackAnimation provides backStyle,
                LocalReducedMotionOverride provides false,
            ) {
                nav = rememberNavController()
                Box(Modifier.fillMaxSize()) {
                    AppNavHost(
                        navController = nav,
                        startDestination = "beneath",
                        pages = PageTransitions(style, reduced = false),
                        gesture = BackGestureTransitions(backStyle, BackGestureSpeed.Standard.ms, reduced = false),
                        beneath = ShellBeneath(),
                        contentAlignment = if (style == PageAnimationType.LinuxWindow) Alignment.BottomCenter else Alignment.Center,
                    ) {
                        page("beneath") { Box(Modifier.fillMaxSize().background(Color.Blue)) }
                        page("top") { Box(Modifier.fillMaxSize().background(Color.Red)) }
                    }
                }
            }
        }
        compose.runOnUiThread { nav.navigate("top") }
        compose.waitForIdle()
        assertEquals(Color.Red, pixelAt(0.5f, 0.5f), "the page on top is open")
    }

    /** The colour at [x], [y] (each 0..1) of the screen. */
    private fun pixelAt(x: Float, y: Float): Color {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return pixels[(pixels.width * x).toInt().coerceAtMost(pixels.width - 1), (pixels.height * y).toInt().coerceAtMost(pixels.height - 1)]
    }

    /** The finger [progress] of the way through a back gesture from [edge], still down. */
    private fun drag(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        val back = compose.activity.onBackPressedDispatcher
        compose.runOnUiThread {
            back.dispatchOnBackStarted(BackEventCompat(0f, 150f, 0f, edge))
            back.dispatchOnBackProgressed(BackEventCompat(60f, 150f, progress, edge))
        }
        compose.waitForIdle()
    }

    @Test
    fun aSlideMovesThePageTowardsTheFinger_wholeToItsEdges() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.SlideHorizontal)
        drag(0.5f)
        dump("slide")
        // Half the gesture is a third of the close: the page is a third of the way off to the right.
        assertEquals(Color.Blue, pixelAt(0.15f, 0.5f), "the page beneath shows where the page has left")
        assertEquals(Color.Red, pixelAt(0.6f, 0.5f))
        assertEquals(Color.Red, pixelAt(0.6f, 0.02f), "it is the whole page, to the top edge - not a card shrunk inside the screen")
        assertEquals(Color.Red, pixelAt(0.98f, 0.98f), "and to the corner")
    }

    @Test
    fun fromTheRightEdgeThePageGoesTheOtherWay() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.SlideHorizontal)
        drag(0.5f, edge = BackEventCompat.EDGE_RIGHT)
        assertEquals(Color.Blue, pixelAt(0.85f, 0.5f))
        assertEquals(Color.Red, pixelAt(0.4f, 0.02f))
    }

    // A page's corners round while it moves (PageWindow), and on this 100 dp screen a corner is a large part of it: the edges are
    // looked at half way along them, where they are straight.

    @Test
    fun aLinuxWindowSinksTowardsTheBottomEdge_asATabDoes() {
        show(PageAnimationType.LinuxWindow)
        drag(0.5f)
        dump("linux_window")
        assertEquals(Color.Blue, pixelAt(0.5f, 0.05f), "the top of the page has sunk away")
        assertEquals(Color.Red, pixelAt(0.5f, 0.9f), "the rest of it is still there")
        assertEquals(Color.Red, pixelAt(0.02f, 0.6f), "as wide as the screen: it is uncovered, not shrunk")
        assertEquals(Color.Red, pixelAt(0.98f, 0.6f))
        // As a tab sinks: the corners that round are the ones of the edge that is sinking; the bottom ones stay the screen's.
        assertEquals(Color.Red, pixelAt(0.02f, 0.98f), "the bottom corners are square")
        assertEquals(Color.Red, pixelAt(0.98f, 0.98f))
    }

    @Test
    fun aFadeFadesThePageWhereItIs() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.FadeThrough)
        drag(0.5f)
        dump("fade")
        // Red, part see-through, over the blue beneath - at the screen's edge too, where a shrunk card would have left plain blue.
        val edge = pixelAt(0.02f, 0.5f)
        assertNotEquals(Color.Red, edge)
        assertNotEquals(Color.Blue, edge)
        assertEquals(edge, pixelAt(0.5f, 0.5f), "the same all over: it has not moved or changed size")
        assertEquals(edge, pixelAt(0.5f, 0.02f))
    }

    @Test
    fun aShrinkIsAndroidsOwn_aLittleSmallerAndTowardsTheFinger() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.System)
        drag(0.5f)
        dump("shrink")
        // A third of the way to nine tenths its size: about 1.5% in from each edge, and a few percent across. Still nearly the screen.
        val middle = pixelAt(0.5f, 0.5f)
        assertNotEquals(Color.Blue, middle)
        assertEquals(middle, pixelAt(0.08f, 0.5f), "nothing like a card at 0.7 of the screen, which would have ended at 0.15")
        assertEquals(middle, pixelAt(0.5f, 0.06f))
    }

    /** What is on screen, to look at: `app/build/qa_back_gesture/`. */
    private fun dump(name: String) {
        val dir = java.io.File("build/qa_back_gesture").also { it.mkdirs() }
        java.io.File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun lettingGoFinishesTheClose_andItIsThePageBeneath() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.SlideHorizontal)
        drag(0.5f)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals("beneath", nav.currentDestination?.route)
        for ((x, y) in listOf(0.02f to 0.02f, 0.5f to 0.5f, 0.98f to 0.98f)) assertEquals(Color.Blue, pixelAt(x, y))
    }

    @Test
    fun theRestOfTheCloseTakesItsTime_itIsNotGoneInAFrame() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.SlideHorizontal)
        drag(0.5f)
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.onBackPressed()
            Snapshot.sendApplyNotifications()
        }
        // Let go half way through the gesture: half the clock is left (210 ms of 420). A few frames in, the page is still leaving.
        compose.mainClock.advanceTimeBy(64)
        assertEquals(Color.Red, pixelAt(0.95f, 0.5f), "still on its way out after 64 ms - it used to be gone in one frame")
        compose.mainClock.advanceTimeBy(600)
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(Color.Blue, pixelAt(0.95f, 0.5f))
    }

    @Test
    fun aGestureGivenUpPutsThePageBack() {
        show(PageAnimationType.LinuxWindow, backStyle = PageAnimationType.SlideHorizontal)
        drag(0.5f)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle()
        assertEquals("top", nav.currentDestination?.route)
        for ((x, y) in listOf(0.02f to 0.5f, 0.5f to 0.5f, 0.98f to 0.5f)) assertEquals(Color.Red, pixelAt(x, y))
    }
}
