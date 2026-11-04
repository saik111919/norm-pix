package com.example.normpix.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.example.normpix.settings.AccentColor
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.ColorSource
import com.example.normpix.settings.ThemeMode
import com.example.normpix.settings.WallpaperPalette
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AssetBitmaps
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import com.example.normpix.welcomeNewInstall
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The welcome tour a new install opens to: four steps, each with something to do or nothing at all, a Skip, a back that steps to
 * the step before, and a last step that ends it. What the first two steps set is in the preferences at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingTourTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: TestEnvironment
    private var finished = false
    private val state get() = env.preferences.state.value

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
        env = TestEnvironment("onboarding_tour")
        env.preferences.startOnboarding()
        File(env.context.filesDir, "profile").deleteRecursively()
    }

    @After
    fun tearDown() {
        env.close()
        File(env.context.filesDir, "profile").deleteRecursively()
    }

    private fun show() {
        compose.setContent { env.Content { OnboardingTour(env.preferences) { finished = true } } }
        compose.waitForIdle()
    }

    private fun next() {
        compose.onNodeWithContentDescription("Next").performClick()
        compose.waitForIdle()
    }

    private fun until(condition: () -> Boolean) = compose.waitUntil(timeoutMillis = 10_000) {
        compose.waitForIdle()
        condition()
    }

    @Test
    fun `the tour goes through its four steps and the last one ends it`() {
        show()
        compose.onNodeWithText("Welcome to NormPix").assertIsDisplayed()
        next()
        compose.onNodeWithText("What should we call you?").assertIsDisplayed()
        next()
        compose.onNodeWithText("Reminders that arrive").assertIsDisplayed()
        compose.onNodeWithText("Allow notifications").assertIsDisplayed()
        next()
        compose.onNodeWithText("You're all set").assertIsDisplayed()
        compose.onAllNodesWithText("Skip").assertCountEquals(0)
        assertFalse(finished)

        compose.onNodeWithContentDescription("Start using NormPix").performClick()
        assertTrue(finished)
    }

    @Test
    fun `the look chosen on the first step is kept at once`() {
        show()
        compose.onNodeWithText("Dark").performClick()
        compose.waitForIdle()
        assertEquals(ThemeMode.Dark, state.mode)

        // The colours are chosen as on Settings > Appearance > Colors: the wallpaper's palettes, or the app's own under "Other colors".
        compose.onNodeWithContentDescription("Vibrant color").performClick()
        compose.waitForIdle()
        assertEquals(WallpaperPalette.Vibrant, state.wallpaperPalette)

        compose.onNodeWithText("Other colors").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Ocean color").performClick()
        compose.waitForIdle()
        assertEquals(AccentColor.Blue, state.accent)
        assertEquals(ColorSource.Custom, state.colorSource)

        compose.onNodeWithText("Wallpaper colors").performClick()
        compose.waitForIdle()
        assertEquals(ColorSource.System, state.colorSource)
    }

    @Test
    fun `a name and an avatar chosen on the second step are the profile`() {
        show()
        next()
        compose.onNodeWithText("Your name").performTextInput("Asha")
        compose.waitForIdle()
        assertEquals("Asha", state.profileName)

        compose.onNodeWithContentDescription("Wear the Fox with goggles avatar").performClick()
        until { state.profilePhoto.isNotBlank() }
        assertTrue(state.profilePhoto.endsWith(".jpg"))
    }

    @Test
    fun `skip goes straight to the app from any step but the last`() {
        show()
        next()
        compose.onNodeWithText("Skip").performClick()
        assertTrue(finished)
    }

    @Test
    fun `skip works on the first step too`() {
        show()
        compose.onNodeWithText("Skip").performClick()
        assertTrue(finished)
    }

    // The pictures ----------------------------------------------------------------------------------------------

    /**
     * The art on screen: one node per step composed, each saying nothing and holding nothing a screen reader would reach. Looked up
     * in the merged tree, the one TalkBack reads: the unmerged tree keeps what `clearAndSetSemantics` hides, for debugging.
     */
    private fun assertArtIsSilent() {
        val arts = compose.onAllNodesWithTag(TOUR_ART_TAG).fetchSemanticsNodes()
        assertTrue(arts.isNotEmpty(), "the step has its picture")
        for (art in arts) {
            assertTrue(art.children.isEmpty(), "nothing inside the picture is in the semantics tree")
            assertFalse(SemanticsProperties.ContentDescription in art.config, "the picture is not described")
            assertFalse(SemanticsProperties.Text in art.config)
        }
    }

    @Test
    fun `every step has a picture that TalkBack passes over`() {
        show()
        repeat(ONBOARDING_STEPS) { step ->
            assertArtIsSilent()
            if (step < ONBOARDING_STEPS - 1) next()
        }
    }

    /** The first step's picture, [ms] further on, on a clock that only moves when told. */
    private fun artAt(ms: Long): IntArray {
        compose.mainClock.advanceTimeBy(ms)
        val image = compose.onNodeWithTag(TOUR_ART_TAG, useUnmergedTree = true).captureToImage()
        return IntArray(image.width * image.height).also { image.readPixels(it) }
    }

    private fun showStill(reduced: Boolean) {
        // Decoded beforehand, so a picture arriving from the disk is not taken for motion.
        tourPictures.forEach { AssetBitmaps.load(env.context, it) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            env.Content {
                CompositionLocalProvider(LocalReducedMotionOverride provides reduced) {
                    Box(Modifier.fillMaxSize()) { OnboardingTour(env.preferences) { finished = true } }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the stickers pop in when a step comes into view`() {
        showStill(reduced = false)
        val early = artAt(48)
        val settled = artAt(3_000)
        assertFalse(early.contentEquals(settled), "a few frames in, the stickers are still on their way")
    }

    @Test
    fun `with animations off the stickers are simply there`() {
        showStill(reduced = true)
        val early = artAt(48)
        val settled = artAt(3_000)
        assertTrue(early.contentEquals(settled), "nothing moves: the first frame is the finished picture")
    }

    @Test
    fun `the picture keeps its place above the title at large text`() {
        compose.setContent {
            env.Content {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                    OnboardingTour(env.preferences) { finished = true }
                }
            }
        }
        compose.waitForIdle()
        val titles = listOf("Welcome to NormPix", "What should we call you?", "Reminders that arrive", "You're all set")
        titles.forEachIndexed { step, title ->
            val art = compose.onNodeWithTag(TOUR_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val heading = compose.onNodeWithText(title).getUnclippedBoundsInRoot()
            assertTrue(heading.top >= art.bottom, "step ${step + 1}: the title starts below the picture ($heading vs $art)")
            if (step < titles.size - 1) next()
        }
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `on a wide window the picture stands beside the title`() {
        show()
        val art = compose.onNodeWithTag(TOUR_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val heading = compose.onNodeWithText("Welcome to NormPix").getUnclippedBoundsInRoot()
        assertTrue(art.left.value >= heading.right.value - 1f, "the picture is to the title's right ($art vs $heading)")
        assertEquals(323f, (art.right - art.left).value, 1f, "at the size it has on a phone")
    }

    @Test
    fun `back steps to the step before`() {
        show()
        next()
        compose.onNodeWithText("What should we call you?").assertIsDisplayed()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Welcome to NormPix").assertIsDisplayed()
        assertFalse(finished, "back is a step, not an exit")
    }

    // Who is welcomed ---------------------------------------------------------------------------------------------

    private fun fresh() = AppPreferences(env.context.getSharedPreferences("welcome_${System.nanoTime()}", 0))

    @Test
    fun `nobody sees the tour unless it was marked as still to come`() {
        val preferences = fresh()
        assertTrue(preferences.state.value.onboardingDone, "the default is done: an existing install is never welcomed")
        assertFalse(preferences.onboardingDecided())
    }

    @Test
    fun `a new install is welcomed once and an update is not`() {
        val brandNew = fresh()
        welcomeNewInstall(brandNew, firstInstallTime = 5_000, lastUpdateTime = 5_000)
        assertFalse(brandNew.state.value.onboardingDone)
        assertTrue(brandNew.onboardingDecided())

        val updated = fresh()
        welcomeNewInstall(updated, firstInstallTime = 5_000, lastUpdateTime = 9_000)
        assertTrue(updated.state.value.onboardingDone, "NormPix was there before this version")
        assertTrue(updated.onboardingDecided(), "and is marked, so it is never welcomed later")

        val silent = fresh()
        welcomeNewInstall(silent, firstInstallTime = 0, lastUpdateTime = 0)
        assertTrue(silent.state.value.onboardingDone, "a phone that will not say is not welcomed")
    }

    @Test
    fun `an app whose storage was cleared is welcomed, though its install looks like an update`() {
        val cleared = fresh()
        welcomeNewInstall(cleared, firstInstallTime = 5_000, lastUpdateTime = 9_000, hadData = false)
        assertFalse(cleared.state.value.onboardingDone, "nothing of NormPix's on the phone: it starts as new")
        assertTrue(cleared.onboardingDecided())

        val updated = fresh()
        welcomeNewInstall(updated, firstInstallTime = 5_000, lastUpdateTime = 9_000, hadData = true)
        assertTrue(updated.state.value.onboardingDone, "an update with its ledger there is not")
    }

    @Test
    fun `a decision already made is left as it is`() {
        val preferences = fresh()
        preferences.startOnboarding()
        welcomeNewInstall(preferences, firstInstallTime = 5_000, lastUpdateTime = 9_000)
        assertFalse(preferences.state.value.onboardingDone)
        preferences.finishOnboarding()
        welcomeNewInstall(preferences, firstInstallTime = 5_000, lastUpdateTime = 5_000)
        assertTrue(preferences.state.value.onboardingDone)
    }

    @Test
    fun `resetting settings and restoring a backup do not touch the tour`() {
        val preferences = fresh()
        preferences.startOnboarding()
        preferences.resetToDefaults()
        assertFalse(preferences.state.value.onboardingDone, "a reset does not bring it back or take it away")
        assertFalse("onboarding_done" in preferences.exportAll(), "a backup does not carry it")
    }
}
