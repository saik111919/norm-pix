package com.example.normpix.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.TestEnvironment
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The cookie's motion as a pure function of time: what it does at each moment, and where it starts and stops. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CookieMotionTest {
    private val endScale = CookieMotion.endScale(screenRadius = 1_000f, radius = 172f)

    @Test
    fun aSpringStartsAtNothing_overshootsALittle_andSettlesAtOne() {
        assertEquals(0f, CookieMotion.spring(0f, 0.6f, 15.1f))
        assertEquals(1f, CookieMotion.spring(1f, 0.6f, 15.1f))
        val samples = (0..200).map { it / 200f }.map { it to CookieMotion.spring(it, 0.6f, 15.1f) }
        val (peakAt, peak) = samples.maxBy { it.second }
        assertTrue(peak in 1.05f..1.12f, "an overshoot of about 9.5%: $peak")
        assertTrue(peakAt in 0.22f..0.30f, "at about a quarter of the way: $peakAt")
        for ((t, v) in samples.filter { it.first >= 0.7f }) assertEquals(1f, v, 0.01f, "settled by $t")
    }

    @Test
    fun theIntroGrowsFromNothingTurnsBackToUprightAndEndsWhereTheExitBegins() {
        val first = CookieMotion.intro(0f)
        assertEquals(0f, first.scale)
        assertEquals(0f, first.lobes)
        assertEquals(0f, first.logoAlpha)
        assertEquals(CookieMotion.LOGO_FROM, first.logoScale, 0.0001f)
        assertTrue(first.rotation < -2f, "it starts turned back about 150 degrees: ${first.rotation}")

        val last = CookieMotion.intro(CookieMotion.INTRO_MS)
        assertEquals(1f, last.scale)
        assertEquals(1f, last.lobes)
        assertEquals(1f, last.logoScale, 0.0001f)
        assertEquals(1f, last.logoAlpha)
        assertEquals(0f, last.rotation, 0.0001f)

        // The exit takes over from exactly the picture the intro leaves: nothing jumps between them.
        val exit = CookieMotion.exit(0f, endScale)
        assertEquals(last.scale, exit.scale, 0.0001f)
        assertEquals(last.rotation, exit.rotation, 0.0001f)
        assertEquals(last.lobes, exit.lobes, 0.0001f)
        assertEquals(1f, exit.fill)
        assertEquals(last.logoScale, exit.logoScale, 0.0001f)
        assertEquals(last.logoAlpha, exit.logoAlpha, 0.0001f)
    }

    @Test
    fun theExitSquishesThenOpens_theMarkSinksFirst_andTheLobesSmoothAway() {
        val squished = CookieMotion.exit(250f, endScale)
        assertEquals(CookieMotion.SQUISH, squished.scale, 0.0001f)
        assertEquals(0f, squished.logoAlpha, 0.0001f, "the mark is gone by the time the cookie has squished")
        assertEquals(0f, squished.fill, 0.0001f, "and the cookie's own colour with it, so the app shows through")

        val end = CookieMotion.exit(CookieMotion.EXIT_MS, endScale)
        assertEquals(endScale, end.scale, 0.0001f)
        assertEquals(CookieMotion.OPEN_TURN, end.rotation, 0.0001f)
        assertEquals(0f, end.lobes, 0.0001f, "a circle by the end")

        var last = CookieMotion.exit(250f, endScale).scale
        for (ms in 260..750 step 10) {
            val scale = CookieMotion.exit(ms.toFloat(), endScale).scale
            assertTrue(scale >= last - 0.0001f, "only grows once it has opened: $ms")
            last = scale
        }
        var lobes = 1f
        for (ms in 250..350 step 10) {
            val now = CookieMotion.exit(ms.toFloat(), endScale).lobes
            assertTrue(now <= lobes + 0.0001f, "the lobes only smooth: $ms")
            lobes = now
        }
        assertEquals(0f, CookieMotion.exit(350f, endScale).lobes, 0.0001f)
    }

    @Test
    fun theOpenedCookieCoversTheScreenBeyondItsCorners() {
        val radius = 172f
        val screenRadius = hypot(411f, 891f) // a corner, from the centre of an 822 x 1782 px screen
        val scale = CookieMotion.endScale(screenRadius, radius)
        assertTrue(radius * scale >= screenRadius, "the circle reaches past the corners")
        assertEquals(CookieMotion.CLEARANCE, radius * scale / screenRadius, 0.0001f)
    }

    @Test
    fun theCookieIsACircleWithNineLobes_andPlainWithNone() {
        val (xs, ys) = CookieMotion.newPoints()
        val path = Path()
        CookieMotion.cookie(path, 100f, 200f, 50f, 0f, 0.7f, xs, ys)
        for (i in xs.indices) assertEquals(50f, hypot(xs[i] - 100f, ys[i] - 200f), 0.01f, "a circle at $i")

        CookieMotion.cookie(path, 100f, 200f, 50f, 3f, 0f, xs, ys)
        val reach = xs.indices.map { hypot(xs[it] - 100f, ys[it] - 200f) }
        assertEquals(53f, reach.max(), 0.05f)
        assertEquals(47f, reach.min(), 0.05f)
        // Nine lobes: the edge crosses the mean radius eighteen times.
        val above = reach.map { it > 50f }
        val crossings = above.indices.count { above[it] != above[(it + 1) % above.size] }
        assertEquals(CookieMotion.LOBES * 2, crossings)
    }
}

/** The gate that lets the opening start once the app below has its frames on time again. */
class CalmFramesTest {
    private val ms = 1_000_000L

    @Test
    fun fourFramesInARowWithoutAStallAreCalm() {
        val calm = CalmFrames()
        assertTrue(!calm.onFrame(0))
        assertTrue(!calm.onFrame(16 * ms))
        assertTrue(!calm.onFrame(32 * ms))
        assertTrue(!calm.onFrame(48 * ms))
        assertTrue(calm.onFrame(64 * ms), "the fourth gap in a row of 16 ms")
    }

    @Test
    fun aSlowButSteadyPaceIsCalm_aStallIsNot() {
        // A slow build draws at about 23 ms a frame even when nothing is wrong: that is calm. A frame that takes 250 ms is a stall.
        val steady = CalmFrames()
        var ok = false
        for (i in 0..4) ok = steady.onFrame(i * 23 * ms)
        assertTrue(ok, "23 ms a frame is a pace, not a stall")

        val calm = CalmFrames()
        listOf(0L, 16L, 32L, 48L).forEach { assertTrue(!calm.onFrame(it * ms)) }
        assertTrue(!calm.onFrame(298 * ms), "a stall of 250 ms: the calm gaps before it do not count")
        assertTrue(!calm.onFrame(314 * ms))
        assertTrue(!calm.onFrame(330 * ms))
        assertTrue(!calm.onFrame(346 * ms))
        assertTrue(calm.onFrame(362 * ms), "four calm ones after it")
    }

    @Test
    fun aGapOfExactlyTheLimitIsStillCalm() {
        val calm = CalmFrames()
        var ok = false
        for (i in 0..4) ok = calm.onFrame(i * 40 * ms)
        assertTrue(ok, "40 ms is the most a calm frame may take")
        val late = CalmFrames()
        var any = false
        for (i in 0..8) any = any || late.onFrame(i * 41 * ms)
        assertTrue(!any, "41 ms is a stall, however steady")
    }
}

/** The cookie on a screen: it holds, opens onto what is under it, and says so when it has. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class CookieSplashTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var env: TestEnvironment? = null

    @After
    fun tearDown() {
        env?.close()
    }

    private val appColor = Color(0xFFE8A33D)

    private fun show(ready: State<Boolean>, events: MutableList<String>, systemGone: State<Boolean> = mutableStateOf(true)) {
        val environment = TestEnvironment(prefsName = "cookie_splash_test").also { env = it }
        environment.preferences.setAccent(AccentColor.Volt)
        environment.preferences.setMode(ThemeMode.Light)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            environment.Content {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(appColor))
                    CookieSplash(
                        ready = ready.value,
                        onFinished = { events += "finished" },
                        onWritten = { events += "written" },
                        systemSplashGone = systemGone.value,
                    )
                }
            }
        }
    }

    private fun pixel(x: Int, y: Int): Int = compose.onRoot().captureToImage().asAndroidBitmap().getPixel(x, y)

    @Test
    fun itHoldsTheSettledCookieUntilTheAppIsReady_thenOpensOntoIt_andOnlyThenIsFinished() {
        val events = mutableListOf<String>()
        val ready = mutableStateOf(false)
        show(ready, events)
        compose.mainClock.advanceTimeBy(900)
        assertEquals(listOf("written"), events, "the intro is done and the app may start its work, but the cookie waits for it")
        // Covering the app: the corner is the page's colour, not the app's.
        assertTrue(pixel(5, 5) != appColor.toArgb(), "the splash covers the app while it waits")

        ready.value = true
        // The clock is by hand, so the write has to be handed to the composition by hand too.
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.advanceTimeBy(CookieMotion.EXIT_MS.toLong() + 100)
        assertEquals(listOf("written", "finished"), events)
        assertEquals(appColor.toArgb(), pixel(5, 5), "the corner is the app's once the cookie has opened past it")
        assertEquals(appColor.toArgb(), pixel(411, 891), "and so is the middle")
    }

    @Test
    fun theIntroDoesNotStartBehindAndroidsLaunchScreen_butDoesNotWaitForeverForIt() {
        // Seen on a phone: the cookie's spin-in played under the system's launch screen, which then faded to a cookie already full size.
        val events = mutableListOf<String>()
        val gone = mutableStateOf(false)
        show(mutableStateOf(true), events, systemGone = gone)
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(emptyList(), events, "the intro has not begun while the launch screen is up")

        gone.value = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy((CookieMotion.INTRO_MS + 100).toLong())
        assertEquals(listOf("written"), events, "it plays once the launch screen has gone")
    }

    @Test
    fun ifTheLaunchScreenNeverReportsGoingTheCookieStartsAnyway() {
        val events = mutableListOf<String>()
        show(mutableStateOf(false), events, systemGone = mutableStateOf(false))
        compose.mainClock.advanceTimeBy(CookieMotion.SYSTEM_SPLASH_WAIT_MS - 100)
        assertEquals(emptyList(), events)
        compose.mainClock.advanceTimeBy(100 + CookieMotion.INTRO_MS.toLong() + 100)
        assertEquals(listOf("written"), events, "after the wait it plays, and holds for the data as usual")
    }

    @Test
    fun aScreenReaderHearsTheNameOnce() {
        val events = mutableListOf<String>()
        show(mutableStateOf(true), events)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, compose.onAllNodes(hasContentDescription("NormPix")).fetchSemanticsNodes().size)
    }
}
