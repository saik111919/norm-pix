package com.example.normpix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.ProgressStyle
import com.example.normpix.testing.TestEnvironment
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppLoadingIndicator
import com.example.normpix.ui.components.LoaderGeometry
import com.example.normpix.ui.components.WavyLinearProgress
import com.example.normpix.ui.components.indeterminateSegments
import com.example.normpix.ui.components.rememberProgressStyle
import com.example.normpix.ui.components.waveAmplitudeFactor
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Progress as Material 3 Expressive draws it: a bar that fills with a wave (flat at empty and full, a dot ending the
 * track, a gap before it), a loader that morphs from shape to shape - and, as the user picks, the flat bar and round
 * spinner Material has always had. One pixel = one dp here (the test density is 160dpi).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class ProgressIndicatorsTest {
    @get:Rule
    val compose = createComposeRule()

    private val env = TestEnvironment("progress_indicators_test")

    @After
    fun tearDown() = env.close()

    // --- the wave ----------------------------------------------------------------------------------------------

    @Test
    fun theWaveRisesOverTheFirstTenth_andSettlesOverTheLastTwentieth() {
        assertEquals(0f, waveAmplitudeFactor(0f), "empty is a plain line")
        assertEquals(0.5f, waveAmplitudeFactor(0.05f), 0.001f)
        assertEquals(1f, waveAmplitudeFactor(0.1f), 0.001f)
        assertEquals(1f, waveAmplitudeFactor(0.6f))
        assertEquals(1f, waveAmplitudeFactor(0.95f), 0.001f)
        assertEquals(0.5f, waveAmplitudeFactor(0.975f), 0.001f)
        assertEquals(0f, waveAmplitudeFactor(1f), "full is a plain line")
    }

    @Test
    fun theIndeterminateStretchesStayOnTheBarAndNeverCross() {
        var sawOne = false
        var sawTwo = false
        for (t in 0L until 3500L step 25L) {
            val segments = indeterminateSegments(t)
            sawOne = sawOne || segments.size == 1
            sawTwo = sawTwo || segments.size == 2
            for (s in segments) assertTrue(s.tail >= 0f && s.head <= 1f && s.tail < s.head, "t=$t: ${s.tail}..${s.head}")
            if (segments.size == 2) {
                val (a, b) = segments.sortedBy { it.tail }
                assertTrue(a.head <= b.tail + 0.001f, "t=$t: the stretches overlap")
            }
        }
        assertTrue(sawOne && sawTwo, "one stretch runs, then the second follows it")
        assertEquals(indeterminateSegments(400).map { it.head }, indeterminateSegments(400 + 1750).map { it.head }, "and it repeats")
    }

    // --- the bar, as drawn -------------------------------------------------------------------------------------

    private val ink = Color.White
    private val track = Color.Red

    private fun PixelMap.isInk(x: Int, y: Int) = this[x, y].let { it.red > 0.6f && it.green > 0.6f && it.blue > 0.6f }
    private fun PixelMap.isTrack(x: Int, y: Int) = this[x, y].let { it.red > 0.6f && it.green < 0.3f }
    private fun PixelMap.isBackground(x: Int, y: Int) = this[x, y].let { it.red < 0.2f && it.green < 0.2f && it.blue < 0.2f }

    /** The rows between the highest and lowest filled pixel in columns [from] until [to]. */
    private fun PixelMap.inkRows(from: Int, to: Int): Int {
        val rows = (0 until height).filter { y -> (from until to).any { x -> isInk(x, y) } }
        return if (rows.isEmpty()) 0 else rows.max() - rows.min() + 1
    }

    private var style by mutableStateOf(ProgressStyle.Wavy)
    private var progress by mutableStateOf<Float?>(0.5f)
    private var thickness by mutableStateOf(4.dp)
    private var shown = false

    /** The bar at [progress], 200dp wide on black: filled part white, track red. One content for the whole test. */
    private fun bar(style: ProgressStyle, progress: Float?, thickness: Dp = 4.dp): PixelMap {
        this.style = style
        this.progress = progress
        this.thickness = thickness
        if (!shown) {
            shown = true
            compose.setContent {
                Box(Modifier.background(Color.Black).testTag("host")) {
                    val p = this@ProgressIndicatorsTest.progress
                    AppLinearProgress(Modifier.width(200.dp), p?.let { v -> { v } }, ink, track, this@ProgressIndicatorsTest.thickness, this@ProgressIndicatorsTest.style)
                }
            }
        }
        compose.waitForIdle()
        return compose.onNodeWithTag("host").captureToImage().toPixelMap()
    }

    @Test
    fun aWavyBarRipplesWhereAFlatOneIsStraight() {
        val wavy = bar(ProgressStyle.Wavy, 0.5f)
        assertTrue(wavy.inkRows(8, 90) >= 8, "the filled part rises and falls: ${wavy.inkRows(8, 90)} rows")
        val flat = bar(ProgressStyle.Flat, 0.5f)
        assertTrue(flat.inkRows(8, 90) <= 5, "a flat one is as thick as it is: ${flat.inkRows(8, 90)} rows")
    }

    @Test
    fun aFullWavyBarSettlesIntoAStraightLine() {
        val full = bar(ProgressStyle.Wavy, 1f)
        assertTrue(full.inkRows(8, 190) <= 5, "no wave left at 100%: ${full.inkRows(8, 190)} rows")
        val empty = bar(ProgressStyle.Wavy, 0f)
        assertEquals(0, empty.inkRows(8, 190), "and none filled at 0%")
        assertTrue(empty.isTrack(100, empty.height / 2), "only the track")
    }

    @Test
    fun aWavyBarLeavesAGapBeforeItsTrack_andEndsTheTrackWithADot() {
        val wavy = bar(ProgressStyle.Wavy, 0.5f)
        val mid = wavy.height / 2
        assertTrue(wavy.isBackground(104, mid), "a gap between the filled part and the track")
        assertTrue(wavy.isTrack(150, mid), "then the track")
        assertTrue(wavy.isInk(198, mid), "ending in a dot of the bar's colour")

        val flat = bar(ProgressStyle.Flat, 0.5f)
        assertTrue(flat.isTrack(104, flat.height / 2), "a flat bar runs straight into its track, as the app's bars always have")
        assertTrue(flat.isTrack(198, flat.height / 2), "with no dot")
    }

    @Test
    fun aFlatBarKeepsTheWeightTheCallerGaveIt() {
        val thick = bar(ProgressStyle.Flat, 0.5f, thickness = 12.dp)
        assertEquals(12, thick.height, "Home's budget bar is as thick as before")
        val wavy = bar(ProgressStyle.Wavy, 0.5f, thickness = 12.dp)
        assertEquals(8 + 6, wavy.height, "a wavy one takes up to 8dp, and the wave's height on top")
    }

    @Test
    fun anIndeterminateWavyBarShowsAStretchOnItsTrack() {
        compose.setContent {
            Box(Modifier.background(Color.Black).testTag("host")) {
                WavyLinearProgress(Modifier.width(200.dp), null, ink, track, 4.dp, travels = false) { 500L }
            }
        }
        compose.waitForIdle()
        val px = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        assertTrue(px.inkRows(0, 200) >= 7, "a wave is running along it")
        assertTrue((0 until 200).any { x -> px.isTrack(x, px.height / 2) }, "over its track")
    }

    private var calmBar by mutableStateOf(false)

    @Test
    fun aCalmBarStaysStraight_whereAWavyOneRises() {
        // A determinate bar at half, its wave held still at a fixed clock; the same content, the calm flag switched on.
        compose.setContent {
            Box(Modifier.background(Color.Black).testTag("host")) {
                WavyLinearProgress(Modifier.width(200.dp), { 0.5f }, ink, track, 4.dp, travels = false, calm = calmBar) { 500L }
            }
        }
        compose.waitForIdle()
        val wavy = compose.onNodeWithTag("host").captureToImage().toPixelMap()
        calmBar = true
        compose.waitForIdle()
        val calm = compose.onNodeWithTag("host").captureToImage().toPixelMap()

        // One pixel is one dp here, so the stroke is 4 rows thick.
        assertTrue(calm.inkRows(8, 90) <= 4 + 1, "a calm bar is as thick as its stroke and no more: ${calm.inkRows(8, 90)} rows")
        assertTrue(
            wavy.inkRows(8, 90) >= calm.inkRows(8, 90) + 3,
            "the wavy bar rises and falls: ${wavy.inkRows(8, 90)} rows against the calm bar's ${calm.inkRows(8, 90)}",
        )
        assertTrue((calm.width - 4 until calm.width).any { x -> calm.isInk(x, calm.height / 2) }, "the calm bar still ends in its dot")
    }

    // --- semantics ---------------------------------------------------------------------------------------------

    @Test
    fun everyProgressStyleReportsItsProgressToTalkBack() {
        compose.setContent {
            Box {
                AppLinearProgress(Modifier.width(100.dp).testTag("known"), { 0.4f }, style = style)
                AppLinearProgress(Modifier.width(100.dp).testTag("unknown"), null, style = style)
                AppLoadingIndicator(Modifier.testTag("loader"), style = style)
            }
        }
        for (each in ProgressStyle.entries) {
            style = each
            compose.waitForIdle()
            compose.onNodeWithTag("known").assertRangeInfoEquals(ProgressBarRangeInfo(0.4f, 0f..1f))
            compose.onNodeWithTag("unknown").assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
            compose.onNodeWithTag("loader").assertRangeInfoEquals(ProgressBarRangeInfo.Indeterminate)
        }
    }

    // --- the loader --------------------------------------------------------------------------------------------

    @Test
    fun theLoaderMorphsThroughSevenShapesWithoutAJump() {
        assertEquals(7, LoaderGeometry.shapeCount)
        val a = Path()
        val b = Path()
        val first = Path().also { LoaderGeometry.at(0, it) }.getBounds()
        val mid = Path().also { LoaderGeometry.at(200, it) }.getBounds()
        assertTrue(abs(first.width - mid.width) > 0.02f || abs(first.height - mid.height) > 0.02f, "it changes shape: $first vs $mid")
        for (step in 1..14) {
            // The end of one shape's morph is the start of the next's, and the turn carries straight on.
            val before = LoaderGeometry.at(step * 650L - 1, a)
            val after = LoaderGeometry.at(step * 650L, b)
            val x = a.getBounds()
            val y = b.getBounds()
            // Bounds are taken from the curves' control points, which two morphs divide differently: close, not equal.
            assertTrue(abs(x.width - y.width) < 0.15f && abs(x.height - y.height) < 0.15f, "step $step: $x vs $y")
            assertTrue(after >= before && after - before < 1f, "step $step turns on: $before -> $after")
            assertTrue(x.width < 2.7f && x.height < 2.7f, "it stays within its box")
        }
    }

    private var loaderSize by mutableStateOf(16.dp)

    @Test
    fun theLoaderFillsItsBoxAtEverySizeTheAppUsesIt() {
        compose.setContent {
            Box(Modifier.background(Color.Black).testTag("host")) { AppLoadingIndicator(Modifier.size(loaderSize), ink, style = ProgressStyle.Wavy) }
        }
        for (each in listOf(16.dp, 24.dp, 28.dp, 48.dp)) {
            loaderSize = each
            compose.waitForIdle()
            val px = compose.onNodeWithTag("host").captureToImage().toPixelMap()
            assertEquals(each.value.toInt(), px.width)
            assertTrue(px.isInk(px.width / 2, px.height / 2), "$each: the shape is in the middle")
            assertTrue(px.isBackground(0, 0), "$each: and leaves the corners clear")
        }
    }

    // --- the choice --------------------------------------------------------------------------------------------

    @Test
    fun withNoPreferences_progressIsWavy() {
        var seen: ProgressStyle? = null
        compose.setContent { seen = rememberProgressStyle() }
        compose.waitForIdle()
        assertEquals(ProgressStyle.Wavy, seen, "as in a preview or a bare test")
    }

    @Test
    fun theStyleIsTheUsersChoice_andChangesAtOnce() {
        var seen: ProgressStyle? = null
        compose.setContent { env.Content { seen = rememberProgressStyle() } }
        compose.waitForIdle()
        assertEquals(ProgressStyle.Wavy, seen, "the default")

        env.preferences.setProgressStyle(ProgressStyle.Flat)
        compose.waitForIdle()
        assertEquals(ProgressStyle.Flat, seen, "and it changes at once, wherever it is on screen")
        assertEquals(ProgressStyle.Flat, env.preferences.state.value.progressStyle)
    }
}
