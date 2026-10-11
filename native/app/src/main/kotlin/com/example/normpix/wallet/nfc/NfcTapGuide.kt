package com.example.normpix.wallet.nfc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCardOff
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PanTool
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.ui.theme.resolve
import kotlinx.coroutines.delay

/** What the tap illustration is showing. */
internal enum class TapPhase { Ready, Reading, Done, Problem, Off }

internal fun NfcInletState.tapPhase(): TapPhase = when (this) {
    NfcInletState.Idle, NfcInletState.Active -> TapPhase.Ready
    NfcInletState.Detected, NfcInletState.Receiving -> TapPhase.Reading
    is NfcInletState.Success, is NfcInletState.PaymentCard -> TapPhase.Done
    is NfcInletState.Unsupported, NfcInletState.Lost, is NfcInletState.Error -> TapPhase.Problem
    NfcInletState.NfcOff, NfcInletState.NoNfc -> TapPhase.Off
}

/** How long the reader waits without a tap before suggesting what to try. */
private const val TIPS_AFTER_MS = 10_000L

/** How long an identified bank card stays on screen before the card form opens. */
private const val HANDOFF_MS = 1_600L

/**
 * The "tap your card" experience for the Inlet: where to hold the card on this phone, what the
 * reader is doing, and what to do next. [acceptsPhones] adds "or a phone sharing a card" to the
 * guidance where the Inlet also receives from other phones.
 */
@Composable
internal fun NfcTapPanel(
    state: NfcInletState,
    area: PhoneNfcArea,
    acceptsPhones: Boolean,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onUsePaymentCard: (PaymentCardInfo) -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
    fillsForm: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberAppHaptics()
    val phase = state.tapPhase()

    // One haptic per tap outcome: the platform's NFC sound is off while the reader runs.
    LaunchedEffect(state) {
        when (state) {
            is NfcInletState.Success -> if (state.alreadySaved) haptics.strong() else haptics.success()
            is NfcInletState.PaymentCard, is NfcInletState.Unsupported, NfcInletState.Lost -> haptics.strong()
            else -> Unit
        }
    }
    // A bank card moves on to the card form by itself after a beat: detection is a step, not a stop.
    val latestUse by rememberUpdatedState(onUsePaymentCard)
    LaunchedEffect(state) {
        if (state is NfcInletState.PaymentCard) {
            delay(HANDOFF_MS)
            latestUse(state.info)
        }
    }

    // Waiting a while with nothing detected is the "not detected" case; Android can't tell a card
    // that's slightly off the antenna from no card at all, so the tips are offered on a timer.
    var waitedLong by remember { mutableStateOf(false) }
    var ripples by remember { mutableIntStateOf(0) }
    LaunchedEffect(phase) {
        waitedLong = false
        if (phase == TapPhase.Ready) {
            delay(TIPS_AFTER_MS)
            waitedLong = true
            ripples++
        }
    }
    val showTips = (phase == TapPhase.Ready && waitedLong) || state == NfcInletState.Lost

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        NfcTapIllustration(area = area, phase = phase, replay = ripples)
        Spacer(Modifier.height(AppSpacing.lg))

        val (title, body) = panelCopy(state, area, acceptsPhones, fillsForm)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = AppSpacing.sm),
        )

        when (state) {
            NfcInletState.Detected, NfcInletState.Receiving -> {
                Spacer(Modifier.height(AppSpacing.lg))
                AppLinearProgress(Modifier.width(160.dp))
            }
            NfcInletState.Idle, NfcInletState.Active, is NfcInletState.Unsupported, NfcInletState.Lost -> {
                Spacer(Modifier.height(AppSpacing.md))
                ListeningTag()
            }
            is NfcInletState.Success -> {
                Spacer(Modifier.height(AppSpacing.lg))
                ReceivedCardPreview(card = state.card)
                TextButton(onClick = onRetry) { Text("Receive another") }
            }
            is NfcInletState.PaymentCard -> {
                Spacer(Modifier.height(AppSpacing.lg))
                Button(onClick = { onUsePaymentCard(state.info) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text("Enter card details")
                }
                TextButton(onClick = onRetry) { Text("Tap another card") }
            }
            NfcInletState.NfcOff -> {
                Spacer(Modifier.height(AppSpacing.lg))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open NFC settings") }
            }
            NfcInletState.NoNfc -> {
                Spacer(Modifier.height(AppSpacing.lg))
                Button(onClick = onEnterManually, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Enter card details") }
            }
            is NfcInletState.Error -> {
                Spacer(Modifier.height(AppSpacing.lg))
                Button(onClick = onRetry) { Text("Try again") }
            }
        }

        AnimatedVisibility(visible = showTips, enter = fadeIn(tween(AppMotion.duration(AppMotion.MEDIUM_MS))), exit = fadeOut()) {
            TapTips(Modifier.padding(top = AppSpacing.lg))
        }
    }
}

/** Title and explanation for each state. Plain words; the reader's limits stated, not hidden. */
private fun panelCopy(state: NfcInletState, area: PhoneNfcArea, acceptsPhones: Boolean, fillsForm: Boolean): Pair<String, String> = when (state) {
    NfcInletState.Idle, NfcInletState.Active -> {
        val what = if (acceptsPhones) {
            "Hold your card flat against the back of your phone, or hold a phone that's sharing a card from NormPix back to back."
        } else {
            "Hold your card flat against the back of your phone."
        }
        val where = when (area) {
            is PhoneNfcArea.Reported -> "Centre it over the marked spot: that's where this phone says its NFC antenna is."
            PhoneNfcArea.Unknown -> "Start near the top, then move it slowly. Where NFC sits differs from phone to phone."
        }
        "Tap your card" to "$what $where"
    }
    NfcInletState.Detected, NfcInletState.Receiving -> "Detected" to "Hold still for a moment."
    is NfcInletState.Success ->
        when {
            state.alreadySaved -> "Already in your wallet" to "You already have this card, so nothing was added."
            // From the card form, a received card fills the form; saving stays the user's call.
            fillsForm -> "Card received" to "Filling in the card form with it."
            else -> "Card added" to "Received from the other phone and saved to your wallet."
        }
    is NfcInletState.PaymentCard ->
        "${state.info.title} detected" to
            "Banks lock the card number inside the chip, so no app can read it with a tap. Add the details yourself, or scan the front of the card."
    is NfcInletState.Unsupported -> state.tag.title to state.tag.message
    NfcInletState.Lost -> "Moved too soon" to "It was detected, then lost before it could be read. Hold it flat and still for a second."
    NfcInletState.NfcOff -> "Turn on NFC" to "NFC is off, so your phone can't sense the card. Turn it on and come back: scanning starts by itself."
    NfcInletState.NoNfc -> "This phone doesn't have NFC" to "You can still add a card by typing its details or scanning the front."
    is NfcInletState.Error -> "Couldn't read that" to state.message
}

/** "Ready for a tap" marker: the reader is listening right now. */
@Composable
private fun ListeningTag() {
    val role = StatusTone.Success.resolve()
    Row(
        Modifier
            .background(role.container, AppRadius.rFull)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(role.color, CircleShape))
        Spacer(Modifier.width(AppSpacing.sm))
        Text("Ready for a tap", style = MaterialTheme.typography.labelLarge, color = role.onContainer)
    }
}

/** What to try when nothing is detected, or the card slipped away. */
@Composable
private fun TapTips(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), color = scheme.surfaceContainerHigh, shape = AppRadius.rLg) {
        Column(Modifier.padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Text("Not picking it up?", style = MaterialTheme.typography.titleSmall)
            Tip(Icons.Outlined.OpenWith, "Move the card slowly over the back: top, middle, then near the camera.")
            Tip(Icons.Outlined.Layers, "Take the card out of its sleeve or wallet, and the phone out of a thick or metal case.")
            Tip(Icons.Outlined.CreditCardOff, "Keep other cards and phones away. Only one can be read at a time.")
            Tip(Icons.Outlined.PanTool, "Once it's detected, hold still for a second.")
        }
    }
}

@Composable
private fun Tip(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(AppSpacing.md))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private val PhoneWidth = 100.dp
private val PhoneHeight = 184.dp
private val PhoneLeft = 32.dp
private val PhoneTop = 4.dp
private val CardWidth = 80.dp
private val CardHeight = 50.dp

/** The shaded "usually here" band on the back, as fractions of the phone, when the phone doesn't report its antenna. */
private const val BAND_TOP = 0.24f
private const val BAND_BOTTOM = 0.60f

/**
 * The back of the phone with where to tap, and a card. When the phone reports its antenna the
 * spot is marked; otherwise the upper half is shaded as a search area, never a made-up spot.
 * Motion explains state: the card slides in once, a few ripples say "listening" and then stop,
 * the card lands on the antenna area when something is detected.
 */
@Composable
private fun NfcTapIllustration(area: PhoneNfcArea, phase: TapPhase, replay: Int) {
    val scheme = MaterialTheme.colorScheme
    val reduced = rememberReducedMotion()
    val warning = StatusTone.Warning.resolve()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    val spots = (area as? PhoneNfcArea.Reported)?.spots.orEmpty()
    val target = spots.firstOrNull()?.let { it.x to it.y } ?: (0.5f to (BAND_TOP + BAND_BOTTOM) / 2)
    val targetX = PhoneLeft + PhoneWidth * target.first
    val targetY = PhoneTop + PhoneHeight * target.second

    val accent by animateColorAsState(
        when (phase) {
            TapPhase.Problem -> warning.color
            TapPhase.Off -> scheme.outline
            else -> scheme.primary
        },
        tween(AppMotion.SHORT_MS),
        label = "nfc_accent",
    )
    val fill = when (phase) {
        TapPhase.Reading, TapPhase.Done -> 0.30f
        TapPhase.Off -> 0.06f
        else -> 0.12f
    }

    // A few ripples when listening starts (and again when tips appear), then still.
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(phase, replay, reduced) {
        ripple.snapTo(0f)
        if (phase == TapPhase.Ready && !reduced) {
            repeat(3) {
                ripple.snapTo(0f)
                ripple.animateTo(1f, tween(1400, easing = LinearOutSlowInEasing))
            }
            ripple.snapTo(0f)
        }
    }

    // The card glides in once, then rests against the phone's edge; on detection it sits on the antenna area.
    val entrance = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) entrance.animateTo(1f, tween(AppMotion.LONG_MS, easing = AppMotion.enter)) }
    val onTarget = phase == TapPhase.Reading || phase == TapPhase.Done
    val restX = PhoneLeft + PhoneWidth - 14.dp
    val restY = (targetY - CardHeight / 2).coerceIn(PhoneTop, PhoneTop + PhoneHeight - CardHeight)
    val cardX by animateDpAsState(if (onTarget) targetX - CardWidth / 2 else restX, AppMotion.spatial(), label = "card_x")
    val cardY by animateDpAsState(if (onTarget) targetY - CardHeight / 2 else restY, AppMotion.spatial(), label = "card_y")

    val description = when (area) {
        is PhoneNfcArea.Reported -> "The back of a phone, with the spot this phone reports for NFC marked, and a card held against it"
        PhoneNfcArea.Unknown -> "The back of a phone, with its upper half shaded where NFC usually is, and a card held against it"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        Box(Modifier.size(width = 216.dp, height = PhoneHeight + PhoneTop * 2)) {
            Canvas(Modifier.offset(PhoneLeft, PhoneTop).size(PhoneWidth, PhoneHeight)) {
                val corner = CornerRadius(18.dp.toPx())
                drawRoundRect(scheme.surfaceContainerHighest, cornerRadius = corner)
                drawRoundRect(scheme.outline, cornerRadius = corner, style = Stroke(1.5.dp.toPx()))
                // Camera module: marks this as the back of the phone.
                val cam = Offset(10.dp.toPx(), 10.dp.toPx())
                drawRoundRect(scheme.onSurfaceVariant.copy(alpha = 0.16f), cam, Size(28.dp.toPx(), 28.dp.toPx()), CornerRadius(8.dp.toPx()))
                drawCircle(scheme.onSurfaceVariant.copy(alpha = 0.35f), 4.dp.toPx(), cam + Offset(9.dp.toPx(), 9.dp.toPx()))
                drawCircle(scheme.onSurfaceVariant.copy(alpha = 0.35f), 4.dp.toPx(), cam + Offset(19.dp.toPx(), 19.dp.toPx()))

                val center = Offset(size.width * target.first, size.height * target.second)
                if (spots.isEmpty()) {
                    val topLeft = Offset(size.width * 0.12f, size.height * BAND_TOP)
                    val bandSize = Size(size.width * 0.76f, size.height * (BAND_BOTTOM - BAND_TOP))
                    val radius = CornerRadius(12.dp.toPx())
                    drawRoundRect(accent.copy(alpha = fill), topLeft, bandSize, radius)
                    drawRoundRect(
                        accent.copy(alpha = 0.7f),
                        topLeft,
                        bandSize,
                        radius,
                        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))),
                    )
                    if (!onTarget) {
                        val label = measurer.measure("Try here", labelStyle.copy(color = accent))
                        drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
                    }
                } else {
                    for (spot in spots) {
                        val c = Offset(size.width * spot.x, size.height * spot.y)
                        drawCircle(accent.copy(alpha = fill * 0.6f), 22.dp.toPx(), c)
                        drawCircle(accent.copy(alpha = fill + 0.1f), 14.dp.toPx(), c)
                        drawCircle(accent, 6.dp.toPx(), c)
                    }
                    if (!onTarget) {
                        // Labelled on whichever side has room, so it never sits on the phone's edge.
                        val label = measurer.measure("NFC", labelStyle.copy(color = accent))
                        val gap = 22.dp.toPx() + 2.dp.toPx()
                        val y = if (center.y < size.height / 2) center.y + gap else center.y - gap - label.size.height
                        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, y))
                    }
                }
                if (ripple.value > 0f) {
                    drawCircle(
                        accent.copy(alpha = 0.45f * (1f - ripple.value)),
                        radius = (16 + 36 * ripple.value).dp.toPx(),
                        center = center,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }
            TapCard(
                done = phase == TapPhase.Done,
                // Read in the layout phase, so the card glides without recomposing every frame.
                modifier = Modifier
                    .offset { IntOffset(cardX.roundToPx(), cardY.roundToPx()) }
                    .graphicsLayer {
                        alpha = entrance.value
                        translationX = (1f - entrance.value) * 36.dp.toPx()
                    },
            )
        }
        Text("Back of phone", style = labelStyle, color = scheme.onSurfaceVariant)
    }
}

/** A generic contactless card. Its antenna's position isn't knowable, so nothing on it claims one. */
@Composable
private fun TapCard(done: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier.size(CardWidth, CardHeight)) {
        Box(
            Modifier
                .size(CardWidth, CardHeight)
                .background(scheme.tertiaryContainer, RoundedCornerShape(7.dp)),
        ) {
            Box(
                Modifier
                    .offset(10.dp, 16.dp)
                    .size(14.dp, 11.dp)
                    .background(scheme.onTertiaryContainer.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
            )
            Icon(
                Icons.Rounded.Contactless,
                contentDescription = null,
                tint = scheme.onTertiaryContainer,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 10.dp, bottom = 9.dp)
                    .size(40.dp, 4.dp)
                    .background(scheme.onTertiaryContainer.copy(alpha = 0.3f), AppRadius.rFull),
            )
        }
        AnimatedVisibility(
            visible = done,
            enter = scaleIn(AppMotion.spatial()) + fadeIn(),
            modifier = Modifier.align(Alignment.TopEnd).offset(8.dp, (-8).dp),
        ) {
            Box(Modifier.size(24.dp).background(scheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}
