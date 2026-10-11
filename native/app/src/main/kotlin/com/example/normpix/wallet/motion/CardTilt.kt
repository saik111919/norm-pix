package com.example.normpix.wallet.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.normpix.ui.theme.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

/**
 * How far the cards lean, in degrees: a hint of depth, not a toy. The card rotates by at most this much about
 * either axis however far the phone is turned.
 */
const val MAX_CARD_TILT_DEGREES = 6f

/**
 * Turns the phone's attitude into a few degrees of card tilt.
 *
 * The card leans by how far the phone has moved *relative to where it has been resting*, not by its absolute
 * angle: the resting pose follows the phone with a time constant of about a second and a half, so a phone that
 * is held still - in the hand or on a table - settles back to a flat card within a few seconds and nothing
 * moves, no matter how it is held. What is left is smoothed, given a small dead zone (hand tremor does not move
 * the card) and clamped to [maxDegrees].
 */
class TiltFilter(
    private val maxDegrees: Float = MAX_CARD_TILT_DEGREES,
    private val gain: Float = 0.5f,
    private val restSeconds: Float = 1.5f,
    private val smoothingSeconds: Float = 0.1f,
    private val deadZoneDegrees: Float = 0.3f,
) {
    /** Card rotation about the horizontal axis (the phone's top leaning away or towards the user), in degrees. */
    var pitch = 0f
        private set

    /** Card rotation about the vertical axis (the phone leaning left or right), in degrees. */
    var roll = 0f
        private set

    private var restPitch = 0f
    private var restRoll = 0f
    private var primed = false

    fun reset() {
        pitch = 0f
        roll = 0f
        primed = false
    }

    /**
     * Feeds the phone's attitude, [dtSeconds] after the previous sample (0 for the first).
     * Returns whether [pitch] or [roll] moved enough to be worth redrawing; false while the phone is steady.
     */
    fun update(phonePitch: Float, phoneRoll: Float, dtSeconds: Float): Boolean {
        if (!primed) {
            restPitch = phonePitch
            restRoll = phoneRoll
            primed = true
            return false
        }
        val dt = dtSeconds.coerceIn(0f, 0.5f)
        val follow = 1f - exp(-dt / restSeconds)
        restPitch += angleDelta(phonePitch, restPitch) * follow
        restRoll += angleDelta(phoneRoll, restRoll) * follow

        val smooth = 1f - exp(-dt / smoothingSeconds)
        val nextPitch = settle(pitch, target(angleDelta(phonePitch, restPitch)), smooth)
        val nextRoll = settle(roll, target(angleDelta(phoneRoll, restRoll)), smooth)
        val moved = abs(nextPitch - pitch) > REDRAW_EPSILON || abs(nextRoll - roll) > REDRAW_EPSILON
        pitch = nextPitch
        roll = nextRoll
        return moved
    }

    private fun target(delta: Float): Float {
        val scaled = delta * gain
        return if (abs(scaled) < deadZoneDegrees) 0f else scaled.coerceIn(-maxDegrees, maxDegrees)
    }

    private fun settle(current: Float, target: Float, smooth: Float): Float {
        val next = current + (target - current) * smooth
        return if (target == 0f && abs(next) < REST_EPSILON) 0f else next
    }

    private companion object {
        const val REDRAW_EPSILON = 0.02f
        const val REST_EPSILON = 0.03f

        /** The shortest signed difference between two angles in degrees. */
        fun angleDelta(to: Float, from: Float): Float {
            var d = (to - from) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }
}

/*
 * The phone's attitude as the direction of "up" in the phone's own axes (ux right, uy towards the top edge, uz out of the
 * screen). Unlike Euler angles it has no gimbal lock when the phone stands upright, which is how it is mostly held.
 */

/** Forward/back lean: 0 with the phone upright, 90 lying face-up, negative leaning forward. */
internal fun pitchDegrees(uy: Float, uz: Float): Float = Math.toDegrees(atan2(uz, uy).toDouble()).toFloat()

/** Left/right lean: positive when the right edge is the higher one. */
internal fun rollDegrees(ux: Float): Float = Math.toDegrees(asin(ux.coerceIn(-1f, 1f)).toDouble()).toFloat()

/** Where the phone's attitude comes from, so the effect can be tested without a sensor. */
interface MotionSource {
    /**
     * Starts delivering attitude as (pitch, roll) in degrees plus the sample's time in nanoseconds:
     * pitch is the forward/back lean (0 when the phone stands upright, 90 when it lies face-up), roll the left/right lean.
     * Returns false when this device has nothing to read; the card then simply stays flat.
     */
    fun start(onAttitude: (pitch: Float, roll: Float, timestampNanos: Long) -> Unit): Boolean

    /** Stops delivering and lets the sensor go. Safe to call when not started. */
    fun stop()
}

/**
 * The device's motion sensors: the fused game rotation vector (gyroscope and accelerometer, no compass to
 * disturb it) where there is one, else the rotation vector, else plain gravity.
 */
class AndroidMotionSource(context: Context) : MotionSource {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private var listener: SensorEventListener? = null

    override fun start(onAttitude: (pitch: Float, roll: Float, timestampNanos: Long) -> Unit): Boolean {
        val manager = manager ?: return false
        val sensor = SENSOR_TYPES.asSequence().mapNotNull { manager.getDefaultSensor(it) }.firstOrNull() ?: return false
        stop()
        val matrix = FloatArray(9)
        val next = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // The unit vector that points up, in the phone's own axes.
                val ux: Float
                val uy: Float
                val uz: Float
                if (event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR || event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(matrix, event.values)
                    ux = matrix[6]
                    uy = matrix[7]
                    uz = matrix[8]
                } else {
                    val length = sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1] + event.values[2] * event.values[2])
                    if (length < 1e-3f) return
                    ux = event.values[0] / length
                    uy = event.values[1] / length
                    uz = event.values[2] / length
                }
                onAttitude(pitchDegrees(uy, uz), rollDegrees(ux), event.timestamp)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        listener = next
        return manager.registerListener(next, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun stop() {
        listener?.let { manager?.unregisterListener(it) }
        listener = null
    }

    private companion object {
        val SENSOR_TYPES = intArrayOf(Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER)
    }
}

/** The card's current lean. Read it where it is drawn (a graphics layer), so a change redraws instead of recomposing. */
@Stable
class CardTiltState {
    var pitch by mutableFloatStateOf(0f)
        internal set
    var roll by mutableFloatStateOf(0f)
        internal set

    internal fun flatten() {
        pitch = 0f
        roll = 0f
    }
}

/** Tests supply a fake; in the app this is null and the device's real sensors are used. */
val LocalMotionSource = staticCompositionLocalOf<MotionSource?> { null }

/**
 * A [CardTiltState] fed by the phone's motion while [enabled] and the screen is in front.
 *
 * The sensor runs only between ON_RESUME and ON_PAUSE of the current lifecycle (so not in the background, and not
 * while another page covers this one), is released the moment [enabled] turns false or this leaves composition, and is
 * never started at all when animations are removed system-wide. Without a sensor the state stays flat.
 */
@Composable
fun rememberCardTilt(enabled: Boolean): CardTiltState {
    val state = remember { CardTiltState() }
    val active = enabled && !rememberReducedMotion()
    val context = LocalContext.current
    val injected = LocalMotionSource.current
    val source = remember(injected, context) { injected ?: AndroidMotionSource(context) }
    LifecycleResumeEffect(active, source) {
        if (active) {
            val filter = TiltFilter()
            var last = 0L
            source.start { pitch, roll, timestamp ->
                val dt = if (last == 0L) 0f else (timestamp - last) / 1e9f
                last = timestamp
                if (filter.update(pitch, roll, dt)) {
                    state.pitch = filter.pitch
                    state.roll = filter.roll
                }
            }
        }
        onPauseOrDispose {
            source.stop()
            state.flatten()
        }
    }
    return state
}
