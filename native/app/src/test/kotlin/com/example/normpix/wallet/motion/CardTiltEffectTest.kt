package com.example.normpix.wallet.motion

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.example.normpix.ui.theme.LocalReducedMotionOverride
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeMotion(private val available: Boolean = true) : MotionSource {
    var starts = 0
    var stops = 0
    var running = false
    private var listener: ((Float, Float, Long) -> Unit)? = null

    override fun start(onAttitude: (pitch: Float, roll: Float, timestampNanos: Long) -> Unit): Boolean {
        starts++
        running = available
        listener = onAttitude
        return available
    }

    override fun stop() {
        stops++
        running = false
        listener = null
    }

    fun sample(pitch: Float, roll: Float, timestampNanos: Long) = listener?.invoke(pitch, roll, timestampNanos)
}

/** The sensor runs only while a card could be seen and the user wants the tilt - and is always let go of. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardTiltEffectTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var enabled by mutableStateOf(true)
    private var shown by mutableStateOf(true)
    private var tilt: CardTiltState? = null

    private fun show(source: MotionSource, reducedMotion: Boolean? = null) {
        compose.setContent {
            CompositionLocalProvider(LocalMotionSource provides source, LocalReducedMotionOverride provides reducedMotion) {
                if (shown) tilt = rememberCardTilt(enabled)
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theSensorRunsWhileEnabledAndIsReleasedWhenTheSettingTurnsOff() {
        val source = FakeMotion()
        show(source)
        assertTrue(source.running)
        assertEquals(1, source.starts)

        compose.runOnUiThread { enabled = false }
        compose.waitForIdle()
        assertFalse(source.running, "released the moment the setting is off")

        compose.runOnUiThread { enabled = true }
        compose.waitForIdle()
        assertTrue(source.running)
    }

    @Test
    fun leavingTheScreenReleasesTheSensor() {
        val source = FakeMotion()
        show(source)
        compose.runOnUiThread { shown = false }
        compose.waitForIdle()
        assertFalse(source.running)
        assertEquals(source.starts, source.stops, "every start is matched by a stop")
    }

    @Test
    fun theSensorStopsInTheBackgroundAndResumesInFront() {
        val source = FakeMotion()
        show(source)
        assertTrue(source.running)

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED) // another page or app in front
        compose.waitForIdle()
        assertFalse(source.running, "not listening while paused")

        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertTrue(source.running)
        assertEquals(source.starts - 1, source.stops, "exactly one listener is live")
    }

    @Test
    fun withAnimationsRemovedTheSensorIsNeverStarted() {
        val source = FakeMotion()
        show(source, reducedMotion = true)
        assertEquals(0, source.starts)
        assertEquals(0f, tilt!!.pitch)
        assertEquals(0f, tilt!!.roll)
    }

    @Test
    fun aDeviceWithoutASensorSimplyStaysFlat() {
        val source = FakeMotion(available = false)
        show(source)
        source.sample(70f, 10f, 1_000_000_000L)
        assertEquals(0f, tilt!!.pitch)
        assertEquals(0f, tilt!!.roll)
    }

    @Test
    fun movingThePhoneLeansTheCardAndPausingFlattensIt() {
        val source = FakeMotion()
        show(source)
        var t = 0L
        compose.runOnUiThread {
            source.sample(60f, 0f, t) // the resting pose
            repeat(15) { source.sample(60f + 10f, 6f, ++t * 20_000_000L) }
        }
        compose.waitForIdle()
        assertTrue(tilt!!.pitch > 0.5f || tilt!!.roll > 0.5f, "leaning: ${tilt!!.pitch}, ${tilt!!.roll}")

        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.waitForIdle()
        assertEquals(0f, tilt!!.pitch)
        assertEquals(0f, tilt!!.roll)
    }
}
