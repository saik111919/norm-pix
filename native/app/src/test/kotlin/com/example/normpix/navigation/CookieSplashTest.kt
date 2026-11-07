package com.example.normpix.navigation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
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
import com.example.normpix.ui.theme.VoltInk
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
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
    fun byTheTimeTheLaunchScreenIsLetGoNothingVisibleIsLeftToMoveInTheIntro() {
        // The launch screen is let go from INTRO_SETTLED_MS, not from the end of the intro (the rest is only the springs' unseen tail),
        // so the cookie under it must be within a hundredth of the settled one by then or the hand-over would show a change.
        val settled = CookieMotion.intro(CookieMotion.INTRO_MS)
        val s = CookieMotion.intro(CookieMotion.INTRO_SETTLED_MS)
        assertTrue(CookieMotion.INTRO_SETTLED_MS < CookieMotion.INTRO_MS)
        assertEquals(settled.scale, s.scale, 0.01f)
        assertEquals(settled.lobes, s.lobes, 0.01f)
        assertEquals(settled.logoScale, s.logoScale, 0.01f)
        assertEquals(settled.logoAlpha, s.logoAlpha, 0.01f)
        assertEquals(settled.rotation, s.rotation, 0.01f)
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

    /** The palette's surface, which is the app's page and so the colour the cookie's ground ends as. */
    private var paletteSurface = 0

    private fun show(
        ready: State<Boolean>,
        events: MutableList<String>,
        launchScreen: State<LaunchScreen> = mutableStateOf(LaunchScreen.None),
    ) {
        val environment = TestEnvironment(prefsName = "cookie_splash_test").also { env = it }
        environment.preferences.setAccent(AccentColor.Volt)
        environment.preferences.setMode(ThemeMode.Light)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            environment.Content {
                paletteSurface = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().background(appColor))
                    CookieSplash(
                        ready = ready.value,
                        onFinished = { events += "finished" },
                        onWritten = { events += "written" },
                        launchScreen = launchScreen.value,
                    )
                }
            }
        }
    }

    /** Android's launch screen, held over the app, whose own intro ends [introEndsInMs] from now; letting it go is noted in [events]. */
    private fun held(events: MutableList<String>, introEndsInMs: Long = 0): LaunchScreen.Held =
        LaunchScreen.Held((compose.mainClock.currentTime + introEndsInMs) * 1_000_000L) { events += "released" }

    private fun handOver(screen: MutableState<LaunchScreen>, to: LaunchScreen) {
        screen.value = to
        // The clock is by hand, so the write has to be handed to the composition by hand too.
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
    }

    private val context get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()

    /** A spot in the cookie's body, left of the mark: 140 px (70 dp at xhdpi) from the middle of the screen. */
    private fun body() = pixel(411 - 140, 891)

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
    fun whileAndroidHasNotHandedOverItsLaunchScreenTheSettledCookieIsAlreadyUnderItAndNothingStarts() {
        // On Android 12 and later the intro is Android's. Until the launch screen is handed over the cookie it ends on is already
        // drawn under it - in its colours, on its ground - so where the system swaps its screen for the app's (a phone showed an
        // empty frame there) the cookie is what shows, not an empty ground. No intro of ours plays and nothing starts.
        val events = mutableListOf<String>()
        show(mutableStateOf(true), events, launchScreen = mutableStateOf(LaunchScreen.Pending))
        compose.mainClock.advanceTimeBy(20)
        assertEquals(context.getColor(com.example.normpix.R.color.splash_cookie), body(), "the settled cookie, from the very first frame")
        assertEquals(context.getColor(com.example.normpix.R.color.window_background), pixel(5, 5), "on the launch screen's ground")
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(emptyList(), events, "the intro has not begun while the launch screen is up")
        assertEquals(context.getColor(com.example.normpix.R.color.splash_cookie), body(), "and it has not moved")
    }

    @Test
    fun ifNoLaunchScreenIsEverHandedOverTheCookiePlaysItsOwnIntroAnyway() {
        // No launch screen was shown (some launches have none): after a short wait the cookie spins in by itself, and holds for the data.
        val events = mutableListOf<String>()
        show(mutableStateOf(false), events, launchScreen = mutableStateOf(LaunchScreen.Pending))
        compose.mainClock.advanceTimeBy(CookieMotion.HANDOVER_WAIT_MS - 200)
        assertEquals(emptyList(), events)
        compose.mainClock.advanceTimeBy(200 + CookieMotion.INTRO_MS.toLong() + 100)
        assertEquals(listOf("written"), events, "after the wait it plays, and holds for the data as usual")
    }

    @Test
    fun aHeldLaunchScreenIsCoveredByTheSettledCookieAtOnceAndOnlyThenReleased() {
        // The system plays the intro itself. Here the cookie it ends on is drawn under it - no intro of ours to play, no beat to wait -
        // and the launch screen is taken away after it has been drawn, so there is nothing to see change. It fades (over the identical
        // cookie), and the app is told it may start once the fade is done.
        val events = mutableListOf<String>()
        val screen = mutableStateOf<LaunchScreen>(LaunchScreen.Pending)
        show(mutableStateOf(false), events, launchScreen = screen)
        compose.mainClock.advanceTimeBy(100)
        handOver(screen, held(events))
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf("released"), events, "covered first; the fade is still going")
        compose.mainClock.advanceTimeBy(CookieMotion.RELEASE_FADE_MS + 100)
        assertEquals(listOf("released", "written"), events, "then the app may start; no intro of its own to play")
        assertTrue(body() != context.getColor(com.example.normpix.R.color.window_background), "the cookie is already full size, not growing")
    }

    @Test
    fun theCookieIsDrawnInTheColourTheLaunchScreenEndedInAndThenTurnsIntoThePalettes() {
        // The launch screen's colours are the wallpaper's accent; the app's palette may be another (Volt here). No jump between them.
        val events = mutableListOf<String>()
        val screen = mutableStateOf<LaunchScreen>(LaunchScreen.Pending)
        show(mutableStateOf(false), events, launchScreen = screen)
        compose.mainClock.advanceTimeBy(100)
        handOver(screen, held(events))
        compose.mainClock.advanceTimeBy(40)
        val launchFill = context.getColor(com.example.normpix.R.color.splash_cookie)
        assertEquals(launchFill, body(), "as the system drew it, as it is let go")
        compose.mainClock.advanceTimeBy(CookieMotion.TINT_MS.toLong() + 100)
        assertEquals(VoltInk.toArgb(), body(), "then the palette's own")
    }

    @Test
    fun theCookieUnderTheLaunchScreenIsThePictureTheLaunchScreenEndsOn() {
        // The two have to be the same picture - size, place, shape, mark, colour - or taking the launch screen away would show a change.
        val events = mutableListOf<String>()
        val screen = mutableStateOf<LaunchScreen>(LaunchScreen.Pending)
        show(mutableStateOf(false), events, launchScreen = screen)
        compose.mainClock.advanceTimeBy(100)
        handOver(screen, held(events))
        compose.mainClock.advanceTimeBy(40)
        val ours = compose.onRoot().captureToImage().asAndroidBitmap()

        // Android draws the icon (288 dp) in the middle of the window, on the window's colour.
        val side = (288 * context.resources.displayMetrics.density).roundToInt()
        val theirs = Bitmap.createBitmap(ours.width, ours.height, Bitmap.Config.ARGB_8888)
        theirs.eraseColor(context.getColor(com.example.normpix.R.color.window_background))
        val left = (ours.width - side) / 2
        val top = (ours.height - side) / 2
        val icon = context.getDrawable(com.example.normpix.R.drawable.splash_icon)!!
        icon.setBounds(left, top, left + side, top + side)
        icon.draw(Canvas(theirs))

        var differing = 0
        val diff = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        for (y in 0 until side) for (x in 0 until side) {
            val a = ours.getPixel(left + x, top + y)
            val b = theirs.getPixel(left + x, top + y)
            val far = maxOf(abs(AndroidColor.red(a) - AndroidColor.red(b)), abs(AndroidColor.green(a) - AndroidColor.green(b)), abs(AndroidColor.blue(a) - AndroidColor.blue(b))) > 48
            if (far) differing++
            diff.setPixel(x, y, if (far) AndroidColor.RED else AndroidColor.rgb(AndroidColor.red(b) / 3, AndroidColor.green(b) / 3, AndroidColor.blue(b) / 3))
        }
        // Kept for looking at: ours | the launch screen's | where they differ.
        val sheet = Bitmap.createBitmap(side * 3, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawBitmap(Bitmap.createBitmap(ours, left, top, side, side), 0f, 0f, null)
        canvas.drawBitmap(Bitmap.createBitmap(theirs, left, top, side, side), side.toFloat(), 0f, null)
        canvas.drawBitmap(diff, 2f * side, 0f, null)
        File("build/launch-splash").apply { mkdirs() }.resolve("cookie-vs-launch-screen.png")
            .outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }

        assertTrue(differing < side * side / 500, "$differing of ${side * side} pixels differ: the cookie is not the launch screen's")
    }

    @Test
    fun aHeldLaunchScreenIsNotTakenAwayBeforeItsOwnIntroHasEnded() {
        // A fast start hands the screen over while its cookie is still spinning in: taking it away then would jump to the settled one.
        val events = mutableListOf<String>()
        val screen = mutableStateOf<LaunchScreen>(LaunchScreen.Pending)
        show(mutableStateOf(false), events, launchScreen = screen)
        compose.mainClock.advanceTimeBy(100)
        handOver(screen, held(events, introEndsInMs = 400))
        compose.mainClock.advanceTimeBy(300)
        assertEquals(emptyList(), events, "its intro has 100 ms to go")
        compose.mainClock.advanceTimeBy(200)
        assertEquals(listOf("released"), events)
        compose.mainClock.advanceTimeBy(CookieMotion.RELEASE_FADE_MS + 100)
        assertEquals(listOf("released", "written"), events)
    }

    @Test
    fun afterAHeldLaunchScreenTheCookieStillHoldsForTheDataAndOpensOntoTheApp() {
        val events = mutableListOf<String>()
        val ready = mutableStateOf(false)
        val screen = mutableStateOf<LaunchScreen>(LaunchScreen.Pending)
        show(ready, events, launchScreen = screen)
        compose.mainClock.advanceTimeBy(100)
        handOver(screen, held(events))
        compose.mainClock.advanceTimeBy(600)
        assertEquals(listOf("released", "written"), events, "still up: the data has not come")
        assertTrue(pixel(5, 5) != appColor.toArgb())

        ready.value = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        compose.mainClock.advanceTimeBy(300)
        compose.mainClock.advanceTimeBy(CookieMotion.EXIT_MS.toLong() + 100)
        assertEquals(listOf("released", "written", "finished"), events)
        assertEquals(appColor.toArgb(), pixel(5, 5), "opened onto the app")
    }

    @Test
    fun theGroundStartsAsTheLaunchScreensAndEndsAsThePalettesSurface_soTheOpeningRevealsAScreenOfItsOwnColour() {
        // The ground is the launch screen's (the window's colour: no change where one gives way to the other) and turns into the
        // palette's surface, which is the page. Recorded on a phone: a greenish ground round a pinkish page, a ring of another shade.
        val events = mutableListOf<String>()
        val screen = mutableStateOf<LaunchScreen>(LaunchScreen.Pending)
        show(mutableStateOf(false), events, launchScreen = screen)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(context.getColor(com.example.normpix.R.color.window_background), pixel(5, 5), "the launch screen's ground at first")
        handOver(screen, held(events))
        compose.mainClock.advanceTimeBy(CookieMotion.RELEASE_FADE_MS + CookieMotion.TINT_MS + 200)
        assertEquals(paletteSurface, pixel(5, 5), "then the palette's surface")
        assertEquals(paletteSurface, pixel(411, 40), "all over the screen the cookie is not on")
    }

    @Test
    fun withNoLaunchScreenTheGroundIsThePalettesSurfaceFromTheStart() {
        show(mutableStateOf(false), mutableListOf())
        compose.mainClock.advanceTimeBy(900)
        assertEquals(paletteSurface, pixel(5, 5))
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-night-xhdpi")
    fun andItFollowsTheSystemsDarkNotTheAppsLightWhichIsWhatTheLaunchScreenDoes() {
        show(mutableStateOf(false), mutableListOf(), launchScreen = mutableStateOf(LaunchScreen.Pending))
        compose.mainClock.advanceTimeBy(900)
        val ground = pixel(5, 5)
        assertEquals(context.getColor(com.example.normpix.R.color.window_background), ground, "the window's dark colour, though the app here is set to Light")
        assertTrue(AndroidColor.red(ground) < 0x40 && AndroidColor.green(ground) < 0x40 && AndroidColor.blue(ground) < 0x40, "and it is dark")
    }

    @Test
    fun aScreenReaderHearsTheNameOnce() {
        val events = mutableListOf<String>()
        show(mutableStateOf(true), events)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, compose.onAllNodes(hasContentDescription("NormPix")).fetchSemanticsNodes().size)
    }
}
