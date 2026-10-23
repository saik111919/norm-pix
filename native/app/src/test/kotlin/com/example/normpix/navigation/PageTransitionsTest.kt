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
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
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
            // A window sinks from the bottom up, so the top of the page it is closing is first to go: a little later than a move shows.
            step(if (type == PageAnimationType.LinuxWindow) 110 else 50)
            val closing = boundsOf("Search settings")
            // A fade through only changes opacity on the way out, which positions can't show.
            if (type != PageAnimationType.FadeThrough) assertTrue(closing != null && closing != rest, "$type: the page has started moving soon after back")
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
    fun goingBackIsTheClosingOfThePageInItsOwnStyle() {
        // Under the back gesture the transition is seeked, not played: what a style does on the way out must be a motion of
        // the page itself, with no delay or keyframe that would hold it still (or jump) while a finger drags it.
        start()
        val fade = movesOf(PageAnimationType.FadeThrough)
        assertTrue(fade.closing.sameSizeAs(fade.rest) && abs(fade.closing.left - fade.rest.left) < 1f, "Fade through: only the opacity changes on the way out")

        val axis = movesOf(PageAnimationType.SharedAxis)
        assertTrue(axis.closing.left > axis.rest.left && axis.closing.sameSizeAs(axis.rest), "Shared axis: glides away sideways, 50 ms in")

        // (The Linux window's close is a plain motion of the page too - its top edge sinks - and is checked, with its geometry,
        // in theLinuxWindowSinksBackIntoItsStripWhileClosing.)
        val scale = movesOf(PageAnimationType.ScaleAndFade)
        assertTrue(scale.closing.width < scale.rest.width && abs(scale.closing.center.x - scale.rest.center.x) < 1f, "Scale and fade: shrinks about its centre")
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

    /**
     * Whether this is what shows of a node that is [full] at rest, while a window's top edge is still below its top: the same
     * left, right and bottom, and a top no higher than it will be. (A node that was scaled or moved would differ in one of them.)
     */
    private fun Rect.isUncoveredPartOf(full: Rect) =
        abs(left - full.left) < 1f && abs(right - full.right) < 1f && abs(bottom - full.bottom) < 1f && top >= full.top - 1f

    /** Whether the node showing [text] is uncovered right now: a node clipped away entirely has no bounds left. */
    private fun shown(text: String): Boolean = boundsOf(text)?.let { it.width > 0f && it.height > 0f } == true

    @Test
    fun theLinuxWindowOpensUpwardFromAStripAtTheBottomWithNothingSquashed() {
        container.preferences.setPageAnimation(PageAnimationType.LinuxWindow)
        start()

        openSettings()
        // Settings' first row sits near the top of the page, "Appearance" lower down. Frame by frame: the lower row is uncovered
        // first (the window's top edge is rising from the bottom), the upper one after it, and never the other way round.
        var lowerFirst = false
        var upperFirst = false
        val seen = mutableListOf<Rect>()
        var waited = 0L
        while (waited < PageTransitions.WINDOW_OPEN_MS + 60) {
            step(16)
            waited += 16
            val upper = shown("Search settings")
            val lower = shown("Appearance")
            if (lower && !upper) lowerFirst = true
            if (upper && !lower) upperFirst = true
            if (lower) seen += boundsOf("Appearance")!!
        }
        val rest = run {
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            boundsOf("Appearance")!!
        }
        assertTrue(lowerFirst, "opening: a row low on the page is uncovered while the top of it is not: it rises from the bottom edge")
        assertTrue(!upperFirst, "opening: the top of the page is never uncovered before what is below it")
        // Uncovered, not scaled or moved: whenever the row shows, it has the same sides and the same bottom as it will finally
        // have - only its top is cut off while the window's top edge is still below it.
        assertTrue(seen.isNotEmpty() && seen.all { it.isUncoveredPartOf(rest) }, "opening: nothing on the page is squashed or shifted on the way ($seen against $rest)")
    }

    @Test
    fun theLinuxWindowSinksBackIntoItsStripWhileClosing_andIsGoneSoonerThanItOpened() {
        container.preferences.setPageAnimation(PageAnimationType.LinuxWindow)
        start()
        openSettings()
        step(1000)
        val rest = boundsOf("Appearance")!!
        assertTrue(shown("Search settings"), "open: the whole page is there")

        back()
        var topGoneFirst = false
        var lowerGoneFirst = false
        var topWentAt = -1L
        val seen = mutableListOf<Rect>()
        var waited = 0L
        while (waited < PageTransitions.WINDOW_CLOSE_MS) {
            step(16)
            waited += 16
            val upper = shown("Search settings")
            val lower = shown("Appearance")
            if (!upper && lower) {
                topGoneFirst = true
                if (topWentAt < 0) topWentAt = waited
            }
            if (upper && !lower) lowerGoneFirst = true
            if (lower) seen += boundsOf("Appearance")!!
        }
        assertTrue(topGoneFirst, "closing: the top of the page goes while the rest is still showing: its top edge is sinking")
        assertTrue(!lowerGoneFirst, "closing: what is low on the page is the last to go")
        assertTrue(seen.isNotEmpty() && seen.all { it.isUncoveredPartOf(rest) }, "closing: nothing on the page is squashed or shifted on the way ($seen against $rest)")

        step(80)
        assertEquals(null, boundsOf("Search settings"), "closing: gone within the (shorter) close duration")
        assertTrue(PageTransitions.WINDOW_CLOSE_MS < PageTransitions.WINDOW_OPEN_MS, "a window closes quicker than it opens")
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
        // The tab is composed on its first visit, which takes the time its arrival would show in: visit it once, and leave.
        compose.onNode(hasContentDescription("Insights")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNode(hasContentDescription("Home")).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        // (The Linux window's tab rises from the bottom edge without moving anything: that is drawn, not placed, so it is
        // checked by pixels in TabArrivalTest.)
        for (type in listOf(PageAnimationType.SlideHorizontal)) {
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
