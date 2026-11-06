package com.example.normpix.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.DockSelectionStyle
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dock pill's way across: its length of time and its two edges, as functions of the flight's progress. */
class DockGlideMathTest {
    @Test
    fun aTapTakes350Ms_aLittleMoreForEachTabItCrosses_andNeverMoreThan400() {
        assertEquals(350, DockGlide.durationMs(1))
        assertEquals(366, DockGlide.durationMs(2))
        assertEquals(382, DockGlide.durationMs(3))
        assertEquals(398, DockGlide.durationMs(4))
        assertEquals(400, DockGlide.durationMs(5), "the cap: 414 would be more")
        assertEquals(400, DockGlide.durationMs(20))
        assertEquals(350, DockGlide.durationMs(0), "no tab at all is the shortest, not a negative")
    }

    @Test
    fun itStartsWhereThePillWasAndEndsOnTheTabItIsGoingTo() {
        assertEquals(70f to 266f, DockGlide.edges(70f, 266f, 560f, 760f, 0f))
        assertEquals(560f to 760f, DockGlide.edges(70f, 266f, 560f, 760f, 1f))
        assertEquals(560f to 760f, DockGlide.edges(70f, 266f, 560f, 760f, 1.5f), "past the end is the end")
    }

    @Test
    fun theEdgeItMovesTowardsRunsAheadSoThePillStretchesInFlight() {
        val startWidth = 266f - 70f
        val endWidth = 760f - 560f
        val widest = (1..99).map { it / 100f }.maxOf { p -> DockGlide.edges(70f, 266f, 560f, 760f, p).let { it.second - it.first } }
        assertTrue(widest > 1.3f * maxOf(startWidth, endWidth), "longest in flight: $widest")

        // The same going back: now the left edge is the one that leads.
        val back = (1..99).map { it / 100f }.maxOf { p -> DockGlide.edges(560f, 760f, 70f, 266f, p).let { it.second - it.first } }
        assertEquals(widest, back, 40f)
    }

    @Test
    fun theEdgesNeverCrossAndOnlyEverMoveTowardsTheirEnd() {
        var last = DockGlide.edges(70f, 266f, 560f, 760f, 0f)
        for (i in 1..200) {
            val now = DockGlide.edges(70f, 266f, 560f, 760f, i / 200f)
            assertTrue(now.first < now.second, "an edge crossed at $i")
            assertTrue(now.first >= last.first - 0.001f && now.second >= last.second - 0.001f, "went back at $i")
            last = now
        }
    }

    @Test
    fun anEndThatIsStillMovingIsFollowed() {
        // The tab it is flying to is growing as it goes: the pill's end is where the tab is at that frame, not where it was.
        val early = DockGlide.edges(70f, 266f, 560f, 700f, 0.9f)
        val late = DockGlide.edges(70f, 266f, 560f, 760f, 0.9f)
        assertTrue(late.second > early.second)
        assertEquals(760f, DockGlide.edges(70f, 266f, 560f, 760f, 1f).second)
    }
}

/** The pill on a real dock: it travels over the tabs between, only in the glide, and always ends on the last tab tapped. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class DockGlideTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    private fun show(style: DockSelectionStyle, reducedMotion: Boolean? = null) {
        val environment = TestEnvironment(prefsName = "dock_glide_test_${style.name}_$reducedMotion").also { env = it }
        environment.preferences.setAccent(AccentColor.Volt)
        environment.preferences.setMode(ThemeMode.Light)
        compose.mainClock.autoAdvance = false
        var current by mutableStateOf(AppDestination.Home)
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides reducedMotion) {
                environment.Content {
                    Box(
                        Modifier.fillMaxWidth().height(110.dp).background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        FloatingNavBar(
                            destinations = AppDestination.entries,
                            current = current,
                            overdueCount = 0,
                            onDestinationSelected = { current = it },
                            selectionStyle = style,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(800)
    }

    /** The colour of the dock a few pixels under the top edge of a tab's own place, in the middle: the pill's, if the pill is there. */
    private fun topOf(description: String): Int {
        val bounds = compose.onNodeWithContentDescription(description).getBoundsInRoot()
        val density = compose.density.density
        val x = ((bounds.left + bounds.right).value / 2f * density).toInt()
        val y = (bounds.top.value * density).toInt() + 3
        return compose.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)
    }

    private fun near(a: Int, b: Int, tolerance: Int = 8): Boolean =
        listOf(16, 8, 0).all { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= tolerance }

    private fun scan(upTo: Long, watching: String, pill: Int): Boolean {
        var seen = false
        var t = 0L
        while (t < upTo) {
            compose.mainClock.advanceTimeBy(20)
            t += 20
            if (near(topOf(watching), pill)) seen = true
        }
        return seen
    }

    @Test
    fun theGlideCarriesTheOnePillOverTheTabsBetween_andLandsOnTheLastOne() {
        show(DockSelectionStyle.Glide)
        val pill = topOf("Home")
        val dock = topOf("Insights")
        assertTrue(!near(pill, dock, 40), "the pill and the dock are different colours")

        compose.onNodeWithContentDescription("Wallet").performClick()
        val passedOver = scan(upTo = 700, watching = "Insights", pill = pill)
        assertTrue(passedOver, "the pill went over the tab between Home and Wallet")
        assertTrue(near(topOf("Wallet"), pill), "and is on Wallet")
        assertTrue(near(topOf("Home"), dock), "and not on Home")
    }

    @Test
    fun theFadeStyleNeverPutsAPillOverTheTabsBetween() {
        show(DockSelectionStyle.Fade)
        val pill = topOf("Home")

        compose.onNodeWithContentDescription("Wallet").performClick()
        val passedOver = scan(upTo = 700, watching = "Insights", pill = pill)
        assertTrue(!passedOver, "each tab's own pill fades; none travels")
        assertTrue(near(topOf("Wallet"), pill), "Wallet has its pill at the end")
    }

    @Test
    fun aSecondTapInFlightEndsOnTheTabTappedLast() {
        show(DockSelectionStyle.Glide)
        val pill = topOf("Home")
        val dock = topOf("Insights")

        compose.onNodeWithContentDescription("Wallet").performClick()
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("Home").performClick()
        compose.mainClock.advanceTimeBy(900)
        assertTrue(near(topOf("Home"), pill), "back on Home")
        assertTrue(near(topOf("Wallet"), dock), "and not on Wallet")
    }

    @Test
    fun withAnimationsOffThePillIsJustOnTheNewTab() {
        show(DockSelectionStyle.Glide, reducedMotion = true)
        val pill = topOf("Home")

        compose.onNodeWithContentDescription("Wallet").performClick()
        compose.mainClock.advanceTimeBy(50)
        assertTrue(near(topOf("Wallet"), pill), "on Wallet at once")
        assertTrue(near(topOf("Insights"), topOf("Home")), "and nowhere on the way")
    }
}
