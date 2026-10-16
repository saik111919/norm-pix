package com.example.normpix.navigation

import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import com.example.normpix.AppContainer
import com.example.normpix.finance.data.FinanceDatabase
import com.example.normpix.finance.data.FinanceRepository
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.reminders.NoOpReminderScheduler
import com.example.normpix.reminders.ReminderDao
import com.example.normpix.reminders.RemindersController
import com.example.normpix.reminders.RemindersDatabase
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.PageAnimationType
import com.example.normpix.settings.ThemeMode
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.ui.theme.Appearance
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pages and tabs really move, in the style the user picked, and the Linux window style opens and closes like a window:
 * the clock is frozen and stepped, and where a page is on screen tells whether (and how) it is animating.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class PageTransitionsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("page_transitions_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        container = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(FinanceDatabase(context, name = null))),
            reminders = RemindersController(ReminderDao(RemindersDatabase(context, name = null)), scheduler = NoOpReminderScheduler),
            vault = InMemoryVault(),
        )
        runBlocking {
            container.finance.load()
            container.reminders.load()
        }
    }

    @After
    fun tearDown() = Unit

    private fun start(reducedMotion: Boolean? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides reducedMotion) { NormPixRoot(container = container) }
        }
        compose.waitForIdle()
    }

    /** Where the one node showing [text] is on screen right now; null when it isn't. */
    private fun boundsOf(text: String): Rect? =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    /** Opens Settings from the More menu, and leaves the clock frozen right after the tap. */
    private fun openSettings() {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        if (compose.onAllNodes(hasContentDescription("Settings")).fetchSemanticsNodes().isEmpty()) {
            compose.onNodeWithContentDescription("More actions").performClick()
            compose.waitForIdle()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun back() = compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }

    private fun step(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    /** The Settings page's own heading, at rest. */
    private fun settledSettingsTitle(): Rect {
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return boundsOf("Search settings")!!
    }

    @Test
    fun everyStyleMovesAPushedPageInAndBackOut() {
        start()
        for (type in PageAnimationType.entries) {
            container.preferences.setPageAnimation(type)
            compose.waitForIdle()

            openSettings()
            step(50)
            val opening = boundsOf("Search settings")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val rest = boundsOf("Search settings")
            assertTrue(opening != null && rest != null && opening != rest, "$type: the page is still moving 50 ms after it started opening")

            compose.mainClock.autoAdvance = false
            back()
            step(50)
            val closing = boundsOf("Search settings")
            // A fade through only changes opacity on the way out, which positions can't show.
            if (type != PageAnimationType.FadeThrough) assertTrue(closing != null && closing != rest, "$type: the page has started moving 50 ms after back")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertEquals(null, boundsOf("Search settings"), "$type: the page is gone once it has closed")
        }
    }

    /** The pushed page, 50 ms into opening and again once settled - and, closing, 50 ms into going back. */
    private class Moves(val opening: Rect, val rest: Rect, val closing: Rect)

    private fun movesOf(type: PageAnimationType): Moves {
        container.preferences.setPageAnimation(type)
        compose.waitForIdle()
        openSettings()
        step(50)
        val opening = boundsOf("Search settings")!!
        val rest = settledSettingsTitle()
        compose.mainClock.autoAdvance = false
        back()
        step(50)
        val closing = boundsOf("Search settings")!!
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        return Moves(opening, rest, closing)
    }

    private fun Rect.sameSizeAs(other: Rect) = abs(width - other.width) < 1f

    @Test
    fun eachStyleMovesThePageTheWayItIsDescribed() {
        start()
        val system = movesOf(PageAnimationType.System)
        val slide = movesOf(PageAnimationType.SlideHorizontal)
        val fade = movesOf(PageAnimationType.FadeThrough)
        val axis = movesOf(PageAnimationType.SharedAxis)
        val scale = movesOf(PageAnimationType.ScaleAndFade)

        // System: a short slide in from the edge at full size; going back the page shrinks as it slides away.
        assertTrue(system.opening.left > system.rest.left && system.opening.sameSizeAs(system.rest), "System: slides in without scaling")
        assertTrue(system.closing.width < system.rest.width && system.closing.left > system.rest.left, "System: shrinks and slides away on back")

        // Slide: the whole page's width, so further than System's short slide; the same both ways.
        assertTrue(slide.opening.left - slide.rest.left > system.opening.left - system.rest.left, "Slide: comes from further than System")
        assertTrue(slide.opening.sameSizeAs(slide.rest) && slide.closing.sameSizeAs(slide.rest), "Slide: moves, doesn't scale")
        assertTrue(slide.closing.left > slide.rest.left, "Slide: goes back out to the side")

        // Fade through: the page waits (small, unseen) while the old one fades out, then scales up as it fades in; no sliding.
        assertTrue(fade.opening.width < fade.rest.width && fade.opening.center.x == fade.rest.center.x, "Fade through: scales up in place")

        // Shared axis: glides a short way along the horizontal axis, no scaling.
        assertTrue(axis.opening.left > axis.rest.left && axis.opening.sameSizeAs(axis.rest), "Shared axis: glides in sideways")

        // Scale and fade: grows into place around its centre.
        assertTrue(scale.opening.width < scale.rest.width && abs(scale.opening.center.x - scale.rest.center.x) < 1f, "Scale and fade: grows from the centre")
        assertTrue(scale.closing.width < scale.rest.width, "Scale and fade: shrinks away on back")
    }

    @Test
    fun theWindowBehindThePagesIsTheAppsOwnBackgroundNotTheSystems() {
        // The app can be dark while the phone is light (or the reverse); wherever no page is drawn - a page arriving,
        // the keyboard resizing - the window shows through, so it has to be the app's background or it flashes.
        var surface = Color.Unspecified
        compose.setContent {
            NormPixTheme(Appearance(mode = ThemeMode.Dark)) { surface = MaterialTheme.colorScheme.surface }
        }
        compose.waitForIdle()
        val window = (compose.activity.window.decorView.background as ColorDrawable).color
        assertEquals(surface.toArgb(), window, "the window background is the app's surface colour")
    }

    @Test
    fun theLinuxWindowGrowsOutOfTheBottomEdgeAndClosesQuicker() {
        container.preferences.setPageAnimation(PageAnimationType.LinuxWindow)
        start()

        openSettings()
        step(60)
        val growing = boundsOf("Search settings")!!
        val rest = settledSettingsTitle()
        // Smaller than it will be, and lower on the screen: it is still coming out of the bottom edge.
        assertTrue(growing.width < rest.width, "opening: scaled up from a smaller window (${growing.width} < ${rest.width})")
        assertTrue(growing.top > rest.top, "opening: rising from below")

        // It has opened by the time the open duration is up.
        compose.mainClock.autoAdvance = false
        back()
        step(PageTransitions.WINDOW_CLOSE_MS.toLong() + 60)
        assertEquals(null, boundsOf("Search settings"), "closing: gone within the (shorter) close duration")
        assertTrue(PageTransitions.WINDOW_CLOSE_MS < PageTransitions.WINDOW_OPEN_MS, "a window closes quicker than it opens")
    }

    @Test
    fun theLinuxWindowShrinksAwayWhileClosing() {
        container.preferences.setPageAnimation(PageAnimationType.LinuxWindow)
        start()
        openSettings()
        step(1000)
        val rest = boundsOf("Search settings")!!

        back()
        step(60)
        val closing = boundsOf("Search settings")!!
        assertTrue(closing.width < rest.width, "closing: shrinking (${closing.width} < ${rest.width})")
        assertTrue(closing.top > rest.top, "closing: shrinking into itself, a little low")
    }

    @Test
    fun withAnimationsRemovedPagesChangeAtOnce() {
        container.preferences.setPageAnimation(PageAnimationType.LinuxWindow)
        start(reducedMotion = true)
        openSettings()
        step(64)
        val soon = boundsOf("Search settings")
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertEquals(boundsOf("Search settings"), soon, "no animation: at rest within a couple of frames, not 280 ms later")
    }

    @Test
    fun aTabComesForwardInTheChosenStyleToo() {
        start()
        for (type in listOf(PageAnimationType.LinuxWindow, PageAnimationType.SlideHorizontal)) {
            container.preferences.setPageAnimation(type)
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            compose.onNode(hasContentDescription("Insights")).performSemanticsAction(SemanticsActions.OnClick)
            // The tab is composed on its first visit a frame or two after the tap; watch it from the first frame it exists.
            var waited = 0L
            while (boundsOf("Nothing to show yet") == null && waited < 400) {
                step(16)
                waited += 16
            }
            step(32)
            val arriving = boundsOf("Nothing to show yet")
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            val rest = boundsOf("Nothing to show yet")
            assertNotEquals(rest, arriving, "$type: the tab is still arriving 60 ms in")
            when (type) {
                PageAnimationType.LinuxWindow -> assertTrue(arriving!!.width < rest!!.width, "grows in place")
                else -> assertTrue(arriving!!.left > rest!!.left, "slides in from the side")
            }
            compose.onNode(hasContentDescription("Home")).performSemanticsAction(SemanticsActions.OnClick)
            compose.waitForIdle()
        }
    }
}
