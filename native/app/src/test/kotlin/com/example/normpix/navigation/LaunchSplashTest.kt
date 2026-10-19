package com.example.normpix.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
import com.example.normpix.testing.InMemoryVault
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.ui.theme.NormPixTheme
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A cold start writes the app's name out - a letter after a letter, then the line under it a word after a word, all off one
 * clock - then opens into the app; with animations removed it goes straight in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class LaunchSplashTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("launch_splash_test", Context.MODE_PRIVATE).also { it.edit().clear().commit() }
        container = AppContainer(
            preferences = AppPreferences(prefs),
            finance = FinanceController(FinanceRepository(FinanceDatabase(context, name = null))),
            reminders = RemindersController(ReminderDao(RemindersDatabase(context, name = null)), scheduler = NoOpReminderScheduler),
            vault = InMemoryVault(),
        )
        runBlocking { container.reminders.load() }
    }

    private fun splashShowing() = compose.onAllNodes(hasContentDescription("NormPix")).fetchSemanticsNodes().isNotEmpty()

    private fun appShowing() = compose.onAllNodes(hasContentDescription("Home")).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun aColdStartWritesTheNameThenOpensIntoTheApp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides false) { NormPixRoot(container, showSplash = true) }
        }
        compose.mainClock.advanceTimeBy(200)
        assertTrue(splashShowing(), "the splash is up at first")

        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 10_000) { !splashShowing() }
        compose.waitUntil(timeoutMillis = 10_000) { appShowing() }
    }

    /** Shows the splash alone, counting how often it says it has written its name and has gone. */
    private fun showSplash(reducedMotion: Boolean = false, ready: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides reducedMotion) {
                NormPixTheme {
                    LaunchSplash(ready = ready, leaving = leaving, onFinished = { finished++ }, onWritten = { written++ })
                }
            }
        }
    }

    private val leaving = Animatable(0f)
    private var written = 0
    private var finished = 0

    /** How high each letter of the name is on screen (a letter that has risen less is lower), in order. */
    private fun letterTops() = "NormPix".map { letter ->
        compose.onNode(hasText(letter.toString()), useUnmergedTree = true).getBoundsInRoot().top.value
    }

    @Test
    fun theLettersDoNotStartBeforeTheSplashHasBeenDrawn() {
        showSplash()
        compose.mainClock.advanceTimeBy(16)
        val first = letterTops()
        assertTrue(first.all { abs(it - first[0]) < 0.5f }, "none has started after one frame: $first")
        assertEquals(0, written)
    }

    @Test
    fun theLettersComeInOneAfterAnother() {
        showSplash()
        compose.mainClock.advanceTimeBy(48)
        val start = letterTops()[6]

        compose.mainClock.advanceTimeBy(250)
        val rising = letterTops()
        // The ones that began first have risen the most (a higher top), and the ones not begun are where they started.
        assertTrue(rising[0] < rising[1] && rising[1] < rising[2] && rising[2] < rising[3], "in order: $rising")
        assertTrue(rising[3] < rising[6], "the last has hardly begun: $rising")
        assertEquals(start, rising[6], 0.5f, "the last letter has not started yet")

        compose.mainClock.advanceTimeBy(SPLASH_WRITE_MS)
        val settled = letterTops()
        assertTrue(settled.all { abs(it - settled[0]) < 0.5f }, "every letter in line once written: $settled")
        assertTrue(settled[0] < start, "and risen from where they began: $settled vs $start")
    }

    @Test
    fun itSaysWhenTheNameIsWrittenThenHoldsAndLeaves() {
        showSplash()
        compose.mainClock.advanceTimeBy(SPLASH_WRITE_MS - 200)
        assertEquals(0, written, "still writing")
        compose.mainClock.advanceTimeBy(400)
        assertEquals(1, written)
        assertEquals(0, finished, "held a moment")
        compose.mainClock.advanceTimeBy(SPLASH_HOLD_MS + SPLASH_EXIT_MS + 200)
        assertEquals(1, finished)
        assertEquals(1, written, "once")
    }

    @Test
    fun itWaitsForTheDataBeforeLeaving() {
        val ready = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides false) {
                NormPixTheme { LaunchSplash(ready = ready.value, leaving = leaving, onFinished = { finished++ }, onWritten = { written++ }) }
            }
        }
        compose.mainClock.advanceTimeBy(SPLASH_WRITE_MS + SPLASH_HOLD_MS + SPLASH_EXIT_MS + 600)
        assertEquals(1, written)
        assertEquals(0, finished, "the data hasn't loaded")
        ready.value = true
        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 10_000) { finished == 1 }
    }

    @Test
    fun withAnimationsRemovedEverythingIsWrittenAtOnce() {
        showSplash(reducedMotion = true)
        compose.mainClock.advanceTimeBy(48)
        val tops = letterTops()
        assertTrue(tops.all { abs(it - tops[0]) < 0.5f }, "all in line already: $tops")
        assertEquals(1, written)
        compose.mainClock.advanceTimeBy(SPLASH_HOLD_MS + 200)
        assertEquals(1, finished, "and gone after the hold, with no fade to wait for")
    }

    @Test
    fun theAppBelowIsNotComposedWhileTheNameIsBeingWritten() {
        // The data is in already, as it is on a quick start: the app could be put up at once, and must not be.
        runBlocking { container.finance.load() }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides false) { NormPixRoot(container, showSplash = true) }
        }
        compose.mainClock.advanceTimeBy(500)
        assertTrue(splashShowing(), "the splash is up")
        assertFalse(appShowing(), "the letters have the main thread to themselves")

        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 10_000) { appShowing() }
        compose.waitUntil(timeoutMillis = 10_000) { !splashShowing() }
    }

    @Test
    fun withAnimationsRemovedTheAppOpensAtOnce() {
        compose.setContent {
            CompositionLocalProvider(LocalReducedMotionOverride provides true) { NormPixRoot(container, showSplash = true) }
        }
        compose.waitForIdle()
        assertTrue(!splashShowing(), "no splash")
    }
}

/** The timeline of the writing, worked out without a screen. */
class SplashTimelineTest {
    private val letters = 7
    private val words = 3

    @Test
    fun `nothing is shown before a letter's time, and all of it after`() {
        for (i in 0 until letters) {
            assertEquals(0f, splashProgress(splashLetterStart(i) - 1f, splashLetterStart(i), SPLASH_LETTER_MS), "letter $i")
            assertEquals(1f, splashProgress((splashLetterStart(i) + SPLASH_LETTER_MS).toFloat(), splashLetterStart(i), SPLASH_LETTER_MS), "letter $i")
            assertEquals(1f, splashProgress(SPLASH_WRITE_MS.toFloat(), splashLetterStart(i), SPLASH_LETTER_MS), "letter $i is in by the end")
        }
        for (i in 0 until words) {
            assertEquals(0f, splashProgress(splashWordStart(i) - 1f, splashWordStart(i), SPLASH_WORD_MS), "word $i")
            assertEquals(1f, splashProgress(SPLASH_WRITE_MS.toFloat(), splashWordStart(i), SPLASH_WORD_MS), "word $i is in by the end")
        }
    }

    @Test
    fun `each steadily comes in, and never goes past its place`() {
        val starts = (0 until letters).map { splashLetterStart(it) to SPLASH_LETTER_MS } + (0 until words).map { splashWordStart(it) to SPLASH_WORD_MS }
        for ((start, duration) in starts) {
            var last = 0f
            var ms = 0f
            while (ms <= SPLASH_WRITE_MS + 50) {
                val now = splashProgress(ms, start, duration)
                assertTrue(now in 0f..1f, "within 0..1 (no overshoot): $now at $ms")
                // The curve is found by bisection to within a thousandth, so it may wobble by a hair; never by more than that.
                assertTrue(now >= last - 0.02f, "never goes back: $now after $last at $ms")
                last = maxOf(last, now)
                ms += 1f
            }
        }
    }

    @Test
    fun `a letter comes in fast and settles`() {
        val half = splashProgress(SPLASH_LETTER_MS / 2f, 0, SPLASH_LETTER_MS)
        assertTrue(half > 0.7f, "most of the way by half the time: $half")
        val early = splashProgress(SPLASH_LETTER_MS / 10f, 0, SPLASH_LETTER_MS)
        assertTrue(early > 0.1f, "under way at once: $early")
    }

    @Test
    fun `the letters come one after another, then the words one after another`() {
        val letterStarts = (0 until letters).map { splashLetterStart(it) }
        assertEquals(letterStarts.sorted(), letterStarts)
        assertEquals(letters, letterStarts.toSet().size, "each its own moment")
        val wordStarts = (0 until words).map { splashWordStart(it) }
        assertEquals(wordStarts.sorted(), wordStarts)
        assertEquals(words, wordStarts.toSet().size, "each its own moment")
        assertTrue(wordStarts.first() > letterStarts.last(), "the line under the name starts once the last letter has")
        assertTrue(wordStarts.first() < letterStarts.last() + SPLASH_LETTER_MS, "while the last letters still settle")
    }

    @Test
    fun `writing the name takes about as long as it did`() {
        assertTrue(SPLASH_WRITE_MS in 900L..1_200L, "about a second: $SPLASH_WRITE_MS")
        assertEquals(splashWordStart(words - 1) + SPLASH_WORD_MS, SPLASH_WRITE_MS, "until the last word is in")
    }
}
