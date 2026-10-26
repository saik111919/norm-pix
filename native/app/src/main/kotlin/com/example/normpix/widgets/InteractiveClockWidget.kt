package com.example.normpix.widgets

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * An interactive, responsive clock widget inspired by a premium watch dial with concentric circular scales:
 * - Concentric scales with fine tick marks (60 ticks, with prominent 5-unit ticks).
 * - Concentric rings with 5-minute numerals (inner and outer scales).
 * - Large central hour numeral (e.g., "12").
 * - A rounded horizontal pill at three o'clock highlighting the selected minute (e.g., "45").
 * - High-precision tap and rotational drag gestures with tick snapping and haptic feedback.
 * - Real-time updates with smooth animation and active accent indicator (#FF8A24).
 * - Full light/dark design tokens integration.
 */
@Composable
fun InteractiveClockWidget(
    modifier: Modifier = Modifier,
    initialHour: Int = 12,
    initialMinute: Int = 45,
    hour: Int? = null,
    minute: Int? = null,
    is24Hour: Boolean = false,
    isInteractive: Boolean = true,
    isLive: Boolean = false,
    tokens: ClockWidgetTokens = rememberClockWidgetTokens(),
    onTimeChanged: ((hour: Int, minute: Int) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current

    // Internal time state if uncontrolled
    var internalHour by remember(initialHour) { mutableIntStateOf(initialHour) }
    var internalMinute by remember(initialMinute) { mutableIntStateOf(initialMinute) }
    var internalSecond by remember { mutableFloatStateOf(0f) }

    val activeHour = hour ?: internalHour
    val activeMinute = minute ?: internalMinute

    val currentOnTimeChanged by rememberUpdatedState(onTimeChanged)

    // Interaction state
    var isDragging by remember { mutableStateOf(false) }
    var previousTouchAngle by remember { mutableFloatStateOf(0f) }

    // Smooth rotational animated offset (in minutes, where 1 minute = 6 degrees)
    val minuteAnimatable = remember { Animatable(activeMinute.toFloat()) }

    // Synchronize animatable when external minute changes and not dragging
    LaunchedEffect(activeMinute, isDragging) {
        if (!isDragging) {
            val currentVal = minuteAnimatable.value
            // Calculate shortest angular distance modulo 60
            val target = activeMinute.toFloat()
            val diff = ((target - currentVal) % 60f + 90f) % 60f - 30f
            minuteAnimatable.animateTo(
                targetValue = currentVal + diff,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    // Live clock ticker
    LaunchedEffect(isLive, isDragging) {
        if (isLive && !isDragging) {
            while (true) {
                val now = LocalTime.now()
                internalHour = if (is24Hour) now.hour else (if (now.hour % 12 == 0) 12 else now.hour % 12)
                internalMinute = now.minute
                internalSecond = now.second + now.nano / 1_000_000_000f
                delay(16L) // ~60fps smooth second hand / ring sweep
            }
        }
    }

    // Active indicator highlight color animation
    val activePillBorder by animateColorAsState(
        targetValue = if (isDragging) tokens.pillBorderActive else tokens.pillBorder,
        animationSpec = tween(durationMillis = 200),
        label = "pillBorder",
    )

    val activeAccentAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0.4f,
        animationSpec = tween(durationMillis = 200),
        label = "accentAlpha",
    )

    val textMeasurer = rememberTextMeasurer()

    val formattedHour = remember(activeHour, is24Hour) {
        if (is24Hour) "%02d".format(activeHour) else "%d".format(activeHour)
    }

    val displayMinuteInt = remember(minuteAnimatable.value) {
        val m = (minuteAnimatable.value.roundToInt() % 60 + 60) % 60
        m
    }

    val semanticsDesc = "Clock widget showing $formattedHour:${"%02d".format(displayMinuteInt)}"

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(tokens.background)
            .semantics {
                role = Role.Button
                contentDescription = semanticsDesc
                stateDescription = "$formattedHour:${"%02d".format(displayMinuteInt)}"
            },
        contentAlignment = Alignment.Center,
    ) {
        val boxWidthPx = with(density) { maxWidth.toPx() }
        val boxHeightPx = with(density) { maxHeight.toPx() }
        val diameter = minOf(boxWidthPx, boxHeightPx)
        val radius = diameter / 2f
        val center = Offset(boxWidthPx / 2f, boxHeightPx / 2f)

        // Gesture handling: Tap and Drag around the dial
        val gestureModifier = if (isInteractive) {
            Modifier
                .fillMaxSize()
                .pointerInput(radius, activeHour) {
                    detectTapGestures { tapOffset ->
                        val dx = tapOffset.x - center.x
                        val dy = tapOffset.y - center.y
                        val dist = hypot(dx, dy)

                        if (dist <= radius * 0.32f) {
                            // Tapped central hour: cycle hour forward
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            val newHour = if (is24Hour) {
                                (activeHour + 1) % 24
                            } else {
                                val h12 = (activeHour % 12) + 1
                                h12
                            }
                            internalHour = newHour
                            currentOnTimeChanged?.invoke(newHour, displayMinuteInt)
                        } else if (dist <= radius) {
                            // Tapped ring: rotate tapped position to 3 o'clock pill
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val tapAngle = atan2(dy, dx) // -PI..PI
                            val tapAngleDeg = (Math.toDegrees(tapAngle.toDouble()).toFloat() + 360f) % 360f
                            // 3 o'clock is 0 deg. Each minute = 6 deg.
                            val minuteDelta = tapAngleDeg / 6f
                            val targetMinute = ((minuteAnimatable.value - minuteDelta) % 60f + 60f) % 60f
                            val snappedMinute = targetMinute.roundToInt() % 60

                            coroutineScope.launch {
                                minuteAnimatable.animateTo(
                                    minuteAnimatable.value - minuteDelta,
                                    spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
                                )
                            }
                            internalMinute = snappedMinute
                            currentOnTimeChanged?.invoke(activeHour, snappedMinute)
                        }
                    }
                }
                .pointerInput(radius) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            val dx = startOffset.x - center.x
                            val dy = startOffset.y - center.y
                            previousTouchAngle = atan2(dy, dx)
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val dx = change.position.x - center.x
                            val dy = change.position.y - center.y
                            val currentTouchAngle = atan2(dy, dx)

                            var angleDiff = currentTouchAngle - previousTouchAngle
                            // Normalize diff to -PI..PI
                            if (angleDiff > PI) angleDiff -= (2 * PI).toFloat()
                            if (angleDiff < -PI) angleDiff += (2 * PI).toFloat()

                            val angleDiffDeg = Math.toDegrees(angleDiff.toDouble()).toFloat()
                            // Rotational dragging: turning finger moves the scale
                            val minuteDelta = angleDiffDeg / 6f

                            coroutineScope.launch {
                                val prevMinuteInt = (minuteAnimatable.value.roundToInt() % 60 + 60) % 60
                                minuteAnimatable.snapTo(minuteAnimatable.value - minuteDelta)
                                val newMinuteInt = (minuteAnimatable.value.roundToInt() % 60 + 60) % 60

                                if (newMinuteInt != prevMinuteInt) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    internalMinute = newMinuteInt
                                    currentOnTimeChanged?.invoke(activeHour, newMinuteInt)
                                }
                            }
                            previousTouchAngle = currentTouchAngle
                        },
                        onDragEnd = {
                            isDragging = false
                            // Snap to nearest minute
                            val targetSnap = minuteAnimatable.value.roundToInt().toFloat()
                            coroutineScope.launch {
                                minuteAnimatable.animateTo(
                                    targetValue = targetSnap,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                                val finalMin = (targetSnap.roundToInt() % 60 + 60) % 60
                                internalMinute = finalMin
                                currentOnTimeChanged?.invoke(activeHour, finalMin)
                            }
                        },
                        onDragCancel = {
                            isDragging = false
                            val targetSnap = minuteAnimatable.value.roundToInt().toFloat()
                            coroutineScope.launch {
                                minuteAnimatable.snapTo(targetSnap)
                            }
                        },
                    )
                }
        } else {
            Modifier.fillMaxSize()
        }

        Box(modifier = gestureModifier) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = center.x
                val cy = center.y
                val r = radius

                // 1. Concentric circular scale radii
                val outerTickOutR = r * 0.98f
                val outerTickMajorInR = r * 0.90f
                val outerTickMinorInR = r * 0.935f

                val outerNumeralR = r * 0.81f

                val midTickOutR = r * 0.69f
                val midTickMajorInR = r * 0.61f
                val midTickMinorInR = r * 0.645f

                val innerNumeralR = r * 0.495f

                // Current animated minute rotation
                val curMinute = minuteAnimatable.value

                // Seconds rotation: in live mode, follows seconds; in interactive mode, stays harmonious
                val curSeconds = if (isLive) internalSecond else (curMinute % 1f) * 60f

                // ==========================================
                // 2. Outer Scale: 60 Radial Tick Marks
                // ==========================================
                for (i in 0 until 60) {
                    val theta = ((curSeconds - i) * 6f) * (PI.toFloat() / 180f)
                    val isMajor = i % 5 == 0
                    val inR = if (isMajor) outerTickMajorInR else outerTickMinorInR
                    val strokeW = if (isMajor) (1.8f * (r / 100.dp.toPx())).coerceAtLeast(1.4f)
                    else (1.0f * (r / 100.dp.toPx())).coerceAtLeast(0.8f)

                    val cosT = cos(theta)
                    val sinT = sin(theta)

                    val startX = cx + inR * cosT
                    val startY = cy + inR * sinT
                    val endX = cx + outerTickOutR * cosT
                    val endY = cy + outerTickOutR * sinT

                    drawLine(
                        color = tokens.tickMarks,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                    )
                }

                // ==========================================
                // 3. Outer Scale: 5-Minute Numerals
                // ==========================================
                val outerFontSize = (r * 0.108f).toSp()
                val outerTextStyle = TextStyle(
                    fontSize = outerFontSize,
                    fontWeight = FontWeight.Medium,
                    color = tokens.secondaryText,
                    textAlign = TextAlign.Center,
                )

                for (v in 0 until 60 step 5) {
                    val theta = ((curSeconds - v) * 6f) * (PI.toFloat() / 180f)
                    val labelText = "%02d".format(v)
                    val layout = textMeasurer.measure(labelText, outerTextStyle)

                    val nx = cx + outerNumeralR * cos(theta) - layout.size.width / 2f
                    val ny = cy + outerNumeralR * sin(theta) - layout.size.height / 2f

                    drawText(
                        textLayoutResult = layout,
                        topLeft = Offset(nx, ny),
                    )
                }

                // ==========================================
                // 4. Middle Scale: 60 Radial Tick Marks
                // ==========================================
                for (i in 0 until 60) {
                    val theta = ((curMinute - i) * 6f) * (PI.toFloat() / 180f)
                    val isMajor = i % 5 == 0
                    val inR = if (isMajor) midTickMajorInR else midTickMinorInR
                    val strokeW = if (isMajor) (1.6f * (r / 100.dp.toPx())).coerceAtLeast(1.2f)
                    else (0.9f * (r / 100.dp.toPx())).coerceAtLeast(0.7f)

                    val cosT = cos(theta)
                    val sinT = sin(theta)

                    val startX = cx + inR * cosT
                    val startY = cy + inR * sinT
                    val endX = cx + midTickOutR * cosT
                    val endY = cy + midTickOutR * sinT

                    // Active tick at 3 o'clock aperture: highlighted with accent
                    val isNearThreeOClock = (theta % (2 * PI.toFloat()) + 2 * PI.toFloat()) % (2 * PI.toFloat())
                    val isActiveTick = isNearThreeOClock < 0.05f || isNearThreeOClock > (2 * PI.toFloat() - 0.05f)

                    val tickColor = if (isActiveTick) {
                        tokens.accent.copy(alpha = (0.5f + 0.5f * activeAccentAlpha).coerceIn(0f, 1f))
                    } else {
                        tokens.tickMarks
                    }

                    drawLine(
                        color = tickColor,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = if (isActiveTick) strokeW * 1.35f else strokeW,
                    )
                }

                // ==========================================
                // 5. Inner Scale: 5-Minute Numerals
                // ==========================================
                val innerFontSize = (r * 0.122f).toSp()
                val innerTextStyle = TextStyle(
                    fontSize = innerFontSize,
                    fontWeight = FontWeight.Medium,
                    color = tokens.secondaryText,
                    textAlign = TextAlign.Center,
                )

                for (v in 0 until 60 step 5) {
                    val theta = ((curMinute - v) * 6f) * (PI.toFloat() / 180f)

                    // Skip the numeral if it's currently inside the 3 o'clock pill
                    // (the pill renders the active minute cleanly with primary text)
                    val normalizedTheta = (theta % (2 * PI.toFloat()) + 2 * PI.toFloat()) % (2 * PI.toFloat())
                    val isInsidePill = normalizedTheta < 0.22f || normalizedTheta > (2 * PI.toFloat() - 0.22f)

                    if (!isInsidePill) {
                        val labelText = "%02d".format(v)
                        val layout = textMeasurer.measure(labelText, innerTextStyle)

                        val nx = cx + innerNumeralR * cos(theta) - layout.size.width / 2f
                        val ny = cy + innerNumeralR * sin(theta) - layout.size.height / 2f

                        drawText(
                            textLayoutResult = layout,
                            topLeft = Offset(nx, ny),
                        )
                    }
                }

                // ==========================================
                // 6. Rounded Pill at Three O'Clock
                // ==========================================
                val pillLeft = cx + r * 0.325f
                val pillRight = cx + r * 0.995f
                val pillHalfH = r * 0.165f
                val pillRadius = pillHalfH

                val pillPath = Path().apply {
                    moveTo(pillRight, cy - pillHalfH)
                    lineTo(pillLeft + pillRadius, cy - pillHalfH)
                    arcTo(
                        rect = Rect(pillLeft, cy - pillHalfH, pillLeft + 2 * pillRadius, cy + pillHalfH),
                        startAngleDegrees = -90f,
                        sweepAngleDegrees = -180f,
                        forceMoveTo = false,
                    )
                    lineTo(pillRight, cy + pillHalfH)
                }

                // Draw pill background
                drawPath(
                    path = pillPath,
                    color = tokens.pillBackground,
                )

                // Draw pill border stroke
                drawPath(
                    path = pillPath,
                    color = activePillBorder,
                    style = Stroke(
                        width = (2.2f * (r / 100.dp.toPx())).coerceAtLeast(1.8f),
                    ),
                )

                // ==========================================
                // 7. Active Selected Minute Numeral inside Pill
                // ==========================================
                val pillMinuteText = "%02d".format(displayMinuteInt)
                val pillMinuteFontSize = (r * 0.155f).toSp()
                val pillMinuteStyle = TextStyle(
                    fontSize = pillMinuteFontSize,
                    fontWeight = FontWeight.Bold,
                    color = tokens.primaryText,
                    textAlign = TextAlign.Center,
                )
                val pillMinuteLayout = textMeasurer.measure(pillMinuteText, pillMinuteStyle)

                // Positioned inside the pill at the inner ring radius
                val pillMinuteX = cx + innerNumeralR - pillMinuteLayout.size.width / 2f
                val pillMinuteY = cy - pillMinuteLayout.size.height / 2f

                drawText(
                    textLayoutResult = pillMinuteLayout,
                    topLeft = Offset(pillMinuteX, pillMinuteY),
                )

                // ==========================================
                // 8. Large Central Hour Numeral
                // ==========================================
                val hourFontSize = (r * 0.38f).toSp()
                val hourStyle = TextStyle(
                    fontSize = hourFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.primaryText,
                    textAlign = TextAlign.Center,
                )
                val hourLayout = textMeasurer.measure(formattedHour, hourStyle)

                // Center coordinates with slight optical alignment
                val hourX = cx - hourLayout.size.width / 2f
                val hourY = cy - hourLayout.size.height / 2f

                drawText(
                    textLayoutResult = hourLayout,
                    topLeft = Offset(hourX, hourY),
                )
            }
        }
    }
}
