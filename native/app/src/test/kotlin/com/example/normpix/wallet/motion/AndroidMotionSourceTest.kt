package com.example.normpix.wallet.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensorManager

/** The real sensor reader against Robolectric's sensor manager: it reads what is there, hands the sensor back, and copes with none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidMotionSourceTest {
    private lateinit var context: Context
    private lateinit var shadow: ShadowSensorManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadow = shadowOf(context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
    }

    private fun addSensor(type: Int): Sensor = org.robolectric.shadows.ShadowSensor.newInstance(type).also { shadow.addSensor(it) }

    private fun send(sensor: Sensor, vararg values: Float) {
        shadow.sendSensorEventToListeners(
            SensorEventBuilder.newBuilder(sensor, values).setTimestamp(123_000_000L).build(),
            sensor,
        )
    }

    @Test
    fun aDeviceWithNoMotionSensorReportsNothingToRead() {
        assertFalse(AndroidMotionSource(context).start { _, _, _ -> })
    }

    @Test
    fun anAccelerometerIsEnoughAndItsGravityBecomesPitchAndRoll() {
        val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
        val source = AndroidMotionSource(context)
        val seen = mutableListOf<Triple<Float, Float, Long>>()
        assertTrue(source.start { pitch, roll, t -> seen += Triple(pitch, roll, t) })

        send(accelerometer, 0f, 9.81f, 0f) // standing upright
        send(accelerometer, 0f, 0f, 9.81f) // lying face-up
        assertEquals(2, seen.size)
        assertEquals(0f, seen[0].first, 0.01f)
        assertEquals(90f, seen[1].first, 0.01f)
        assertEquals(123_000_000L, seen[0].third)
    }

    @Test
    fun stoppingStopsDeliveryAndStartingAgainDoesNotDouble() {
        val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
        val source = AndroidMotionSource(context)
        var count = 0
        source.start { _, _, _ -> count++ }
        source.start { _, _, _ -> count += 100 } // a second start replaces the first listener rather than adding one
        send(accelerometer, 0f, 9.81f, 0f)
        assertEquals(100, count)

        source.stop()
        send(accelerometer, 0f, 9.81f, 0f)
        assertEquals(100, count, "nothing is delivered after stop")
        source.stop() // harmless when already stopped
    }

    @Test
    fun theFusedGameRotationVectorIsPreferredOverTheAccelerometer() {
        val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
        val game = addSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        var calls = 0
        AndroidMotionSource(context).start { _, _, _ -> calls++ }
        send(accelerometer, 0f, 9.81f, 0f)
        assertEquals(0, calls, "not listening to the accelerometer")
        // A rotation vector of "no rotation": the phone flat on the table, so up is straight out of the screen.
        send(game, 0f, 0f, 0f, 1f)
        assertEquals(1, calls)
    }
}
