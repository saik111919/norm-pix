package com.example.normpix.wallet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.settings.WalletCardActionStyle
import com.example.normpix.ui.components.DeckCardSpace
import com.example.normpix.ui.components.SwipeRevealRow
import com.example.normpix.ui.components.rememberAppHaptics
import kotlin.math.roundToInt
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.wallet.clipboard.CardField
import com.example.normpix.wallet.motion.CardTiltState
import com.example.normpix.wallet.motion.MAX_CARD_TILT_DEGREES
import kotlinx.coroutines.launch

/** Subtle foil emboss shadow behind card text, like stamped physical bank cards. */
internal val embossShadow = Shadow(Color.Black.copy(alpha = 0.33f), Offset(0f, 1.5f), 4f)

/**
 * The colour of a card face's small captions ("BALANCE", "EXPIRES", "% used"): white at 90%, not the 62% they had, which fell to
 * 2.4-4.3:1 on several banks' colours (measured against every bank theme; text this small needs 4.5:1). Hierarchy on a card
 * is made by size and weight, not by dimming - see CardTextContrastTest.
 */
internal val CardCaptionColor = Color.White.copy(alpha = 0.9f)

internal const val CARD_ASPECT = 1.586f

/** Groups digits in fours: `1234 5678 ...`. */
fun groupDigits(digits: String): String = digits.chunked(4).joinToString(" ")

/**
 * A credit/debit card rendered as a realistic card face. Tapping the card flips
 * it (like a physical card) to its back, which shows the CVV.
 *
 * Reveal / edit / delete are exposed per [actionStyle]: a row of buttons under
 * the card, or a right-to-left swipe. Either way the same actions are also
 * available to screen readers as custom accessibility actions, so no action
 * depends on a gesture alone.
 *
 * With a [tilt] the card leans a few degrees as the phone moves and a soft sheen follows the lean; both are
 * read while drawing, so a moving phone redraws the card without recomposing it.
 */
@Composable
fun WalletCardView(
    card: StoredCard,
    revealed: Boolean,
    /** How the card's controls are reached; null shows the card alone, for a caller that lays the controls out itself ([CardActionBar]). */
    actionStyle: WalletCardActionStyle?,
    onToggleReveal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** When true, [onToggleReveal] asks for an NFC tag tap before revealing, and the reveal control says so. */
    nfcGated: Boolean = false,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
    /** Sends this card to another phone over NFC; no share control is shown when null. */
    onShareNfc: (() -> Unit)? = null,
    /** The phone's motion as a lean; null keeps the card flat. */
    tilt: CardTiltState? = null,
    /** Copies one [CardField] straight away, or - with null - opens the choice of what to copy. No copy control is shown when this is null. */
    onCopy: ((CardField?) -> Unit)? = null,
    /** Set for a card standing in a deck: where it is drawn, so its print is scaled to the card's width and kept clear of the panel. */
    deck: DeckCardSpace? = null,
) {
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    val flip = remember { Animatable(0f) }
    val theme = remember(card.bankName) { BankCardDesign.themeFor(card.bankName) }
    val showingBack = flip.value >= 0.5f

    val revealLabel = revealLabelOf(revealed, nfcGated)
    val revealIcon = revealIconOf(revealed, nfcGated)

    fun doFlip() {
        haptics.selection()
        scope.launch {
            val target = if (flip.targetValue == 0f) 1f else 0f
            flip.animateTo(target, tween(if (reduced) 0 else 500, easing = AppMotion.standard))
        }
    }

    // The back shows the CVV, when one is kept; a card without one still turns over, to nothing but "No CVV".
    val showBack = if (card.hasCvv) "Shows the back and CVV" else "Shows the back"
    val kind = if (card.kind == CardKind.Credit) "credit" else "debit"
    val number = if (revealed) "number ${groupDigits(card.cardNumber)}" else "ending in ${card.last4}"
    val spoken = "${card.bankName} $kind card, $number, expires ${card.expiryMonth}/${card.expiryYear}, ${card.cardholderName}"

    // Card art keeps a physical card's proportions, so it ignores the system font scale. In a deck the card is narrower than a
    // card of a list: its print is drawn as it is on that one and shrunk to fit - spacing and size together, so the card is as
    // roomy as the big one - and the card runs on under the panel, so the print keeps its margin above the panel's edge.
    val density = LocalDensity.current
    val printScale = deck?.let { (it.width / PrintReferenceWidth).coerceIn(0.5f, 1f) } ?: 1f
    // Type shrinks less than the rest: a caption of 9sp at 83% is a caption of 7.5sp.
    val textScale = maxOf(printScale, MinTypeScale)
    val fixedDensity = remember(density, printScale, textScale) {
        Density(density.density * printScale, fontScale = textScale / printScale)
    }
    val underlapPx = with(density) { (deck?.underlap ?: 0.dp).roundToPx() }
    val underlap = (deck?.underlap ?: 0.dp) / printScale

    val flipCard: @Composable () -> Unit = {
        CompositionLocalProvider(LocalDensity provides fixedDensity) {
            val angle = flip.value * 180f
            val lift = kotlin.math.sin(flip.value * Math.PI).toFloat() * 0.04f
            Box(
                Modifier
                    .fillMaxWidth()
                    .cardProportions(underlapPx)
                    .clickable(onClickLabel = if (showingBack) "Shows the front" else showBack, onClick = ::doFlip)
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Button
                        stateDescription = if (showingBack) "Showing the back" else "Showing the front"
                        onClick(label = if (showingBack) "Shows the front" else showBack) { doFlip(); true }
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(revealLabel) { onToggleReveal(); true },
                            onShareNfc?.let { share -> CustomAccessibilityAction("Share via NFC") { share(); true } },
                        ) + CardField.entries.filter { it != CardField.Cvv || card.hasCvv }.mapNotNull { field ->
                            onCopy?.let { copy -> CustomAccessibilityAction("Copy ${field.label.lowercase()}") { copy(field); true } }
                        } + listOf(
                            CustomAccessibilityAction("Edit card") { onEdit(); true },
                            CustomAccessibilityAction("Delete card") { onDelete(); true },
                        )
                    }
                    .graphicsLayer {
                        cameraDistance = 12f * density.density
                        scaleX = 1f + lift
                        scaleY = 1f + lift
                        rotationX = tilt?.let { -it.pitch } ?: 0f
                        rotationY = angle + (tilt?.roll ?: 0f)
                    }
                    .then(if (tilt != null) Modifier.drawWithContent { drawContent(); drawSheen(tilt) } else Modifier),
            ) {
                if (angle > 90f) {
                    Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { CardBack(card, theme, revealed, underlap) }
                } else {
                    CardFront(card, theme, revealed, bankIconStyle, underlap)
                }
            }
        }
    }

    if (actionStyle == WalletCardActionStyle.Swipe) {
        val scheme = MaterialTheme.colorScheme
        SwipeRevealRow(
            actionsWidth = 264.dp + (if (onShareNfc != null) 88.dp else 0.dp) + (if (onCopy != null) 88.dp else 0.dp),
            modifier = modifier.clip(AppRadius.rLg),
            actions = {
                CardRevealAction(revealIcon, if (revealed) "Hide" else "Reveal", scheme.secondaryContainer, scheme.onSecondaryContainer, onToggleReveal)
                if (onCopy != null) {
                    CardRevealAction(Icons.Outlined.ContentCopy, "Copy", scheme.primaryContainer, scheme.onPrimaryContainer) { onCopy(null) }
                }
                if (onShareNfc != null) {
                    CardRevealAction(Icons.Rounded.Contactless, "Share", scheme.tertiaryContainer, scheme.onTertiaryContainer, onShareNfc)
                }
                CardRevealAction(Icons.Outlined.Edit, "Edit", scheme.surfaceContainerHighest, scheme.onSurfaceVariant, onEdit)
                CardRevealAction(Icons.Outlined.DeleteOutline, "Delete", scheme.errorContainer, scheme.onErrorContainer, onDelete)
            },
        ) { flipCard() }
        return
    }

    Column(modifier) {
        flipCard()
        if (actionStyle != null) {
            Spacer(Modifier.height(AppSpacing.md))
            CardActionBar(
                revealed = revealed,
                nfcGated = nfcGated,
                onToggleReveal = onToggleReveal,
                onEdit = onEdit,
                onDelete = onDelete,
                onShareNfc = onShareNfc,
                onCopy = onCopy?.let { copy -> { copy(null) } },
            )
        }
    }
}

private fun revealLabelOf(revealed: Boolean, nfcGated: Boolean): String = when {
    revealed -> "Hide"
    nfcGated -> "Tap NFC to reveal"
    else -> "Reveal"
}

private fun revealIconOf(revealed: Boolean, nfcGated: Boolean): ImageVector = when {
    revealed -> Icons.Outlined.VisibilityOff
    nfcGated -> Icons.Outlined.Nfc
    else -> Icons.Outlined.Visibility
}

/**
 * A card's controls as a row of buttons: Reveal (or Hide, or "Tap NFC to reveal") across the row, then Copy, Share and Edit and
 * Delete as icons. Under each card of the card list, and under the carousel's card in front. Copy and Share are left out when
 * [onCopy] or [onShareNfc] is null.
 */
@Composable
internal fun CardActionBar(
    revealed: Boolean,
    nfcGated: Boolean,
    onToggleReveal: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onShareNfc: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    /**
     * For a narrow bar (the carousel's panel, 288dp on a 360dp phone). The icon buttons take 48dp each - their touch targets - which
     * leaves Reveal about 76dp, and a tonal button's usual 24dp either side left its label none ("Reveal" read as "."). Compact:
     * the button's padding is tighter, and no gap is added between the icon buttons, whose targets are already 8dp apart.
     */
    compact: Boolean = false,
) {
    val gap = if (compact) 0.dp else AppSpacing.xs
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = onToggleReveal,
            modifier = Modifier.weight(1f),
            contentPadding = if (compact) PaddingValues(horizontal = 10.dp) else ButtonDefaults.ContentPadding,
        ) {
            Icon(revealIconOf(revealed, nfcGated), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(if (compact) 6.dp else AppSpacing.sm))
            Text(revealLabelOf(revealed, nfcGated), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(if (compact) AppSpacing.xs else AppSpacing.sm))
        if (onCopy != null) {
            FilledTonalIconButton(onClick = onCopy) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy card details") }
            Spacer(Modifier.width(gap))
        }
        if (onShareNfc != null) {
            FilledTonalIconButton(onClick = onShareNfc) { Icon(Icons.Rounded.Contactless, contentDescription = "Share via NFC") }
            Spacer(Modifier.width(gap))
        }
        FilledTonalIconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "Edit card") }
        Spacer(Modifier.width(gap))
        FilledTonalIconButton(onClick = onDelete) { Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete card") }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.CardRevealAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    background: Color,
    foreground: Color,
    onClick: () -> Unit,
) {
    Column(
        Modifier.weight(1f).fillMaxHeight().background(background).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = foreground)
        Text(label, style = MaterialTheme.typography.labelMedium, color = foreground)
    }
}

/**
 * A soft contact shadow cut exactly to the card: the card's own rounded outline, grown in a few steps and shifted
 * down a little, each step faint, so it fades outwards and its corners follow the card's corners.
 */
private fun DrawScope.drawCardShadow() {
    val steps = 6
    val reach = 7.dp.toPx()
    val drop = 3.dp.toPx()
    for (i in steps downTo 1) {
        val grow = reach * i / steps
        val outline = AppRadius.rLg.createOutline(Size(size.width + 2 * grow, size.height + 2 * grow), layoutDirection, this)
        translate(left = -grow, top = -grow + drop) {
            drawOutline(outline, Color.Black, alpha = 0.04f)
        }
    }
}

/** A soft highlight that slides across the card as it leans, like light moving over a glossy surface. */
internal fun DrawScope.drawSheen(tilt: CardTiltState) {
    val x = (tilt.roll / MAX_CARD_TILT_DEGREES).coerceIn(-1f, 1f)
    val y = (tilt.pitch / MAX_CARD_TILT_DEGREES).coerceIn(-1f, 1f)
    if (x == 0f && y == 0f) return
    // Painted through the card's own outline, so the highlight never leaks past the rounded corners.
    drawOutline(
        AppRadius.rLg.createOutline(size, layoutDirection, this),
        Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.16f),
            1f to Color.Transparent,
            center = Offset(size.width * (0.5f + 0.45f * x), size.height * (0.5f + 0.45f * y)),
            radius = size.width * 0.7f,
        ),
    )
}

@Composable
private fun CardFront(card: StoredCard, theme: BankBrandTheme, revealed: Boolean, bankIconStyle: BankIconStyle, bottomInset: Dp = 0.dp) {
    val network = CardNetwork.fromNumber(card.cardNumber)
    CardFace(theme, bottomInset = bottomInset) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f, fill = false)) { BankLogoView(card.bankName, theme, compact = bankIconStyle == BankIconStyle.Compact) }
                Spacer(Modifier.width(8.dp))
                CardBadge(
                    text = if (card.kind == CardKind.Credit) "CREDIT" else "DEBIT",
                    background = theme.badgeColor,
                    border = theme.badgeBorderColor,
                    textColor = theme.badgeTextColor,
                )
            }
            // The chip sits where it does on a physical card, just above the number; what is left of the card above it is
            // the bank's pattern.
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                EmvChip(theme.chipFinish)
                Spacer(Modifier.width(10.dp))
                ContactlessMark()
            }
            Spacer(Modifier.height(14.dp))
            CardNumber(card, revealed)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                CardDetail("CARDHOLDER", card.cardholderName.uppercase(), Modifier.weight(1f))
                CardDetail("EXPIRES", "${card.expiryMonth}/${card.expiryYear}")
                if (network != CardNetwork.Unknown) {
                    Box(Modifier.padding(bottom = 1.dp)) { CardNetworkBadge(network, 24.dp) }
                }
            }
        }
    }
}

/** The number: four dots to a group while hidden, with the last four in clear; the whole of it, in groups, once revealed. */
@Composable
private fun CardNumber(card: StoredCard, revealed: Boolean) {
    val style = TextStyle(
        color = Color.White,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        letterSpacing = 1.2.sp,
        fontFeatureSettings = "tnum",
        shadow = embossShadow,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (revealed) {
            for (group in groupDigits(card.cardNumber).split(' ')) Text(group, style = style, maxLines = 1)
        } else {
            repeat(3) { MaskedGroup() }
            Text(card.last4, style = style, maxLines = 1)
        }
    }
}

@Composable
private fun MaskedGroup() {
    Canvas(Modifier.size(width = 34.dp, height = 10.dp)) {
        val radius = 2.6.dp.toPx()
        val step = (size.width - 2 * radius) / 3
        for (i in 0..3) drawCircle(Color.White.copy(alpha = 0.88f), radius, Offset(radius + step * i, size.height / 2))
    }
}

/** A small caps label over what it names. */
@Composable
internal fun CardDetail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = TextStyle(color = CardCaptionColor, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, letterSpacing = 0.9.sp, shadow = embossShadow),
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.5.sp,
                letterSpacing = 0.6.sp,
                fontFeatureSettings = "tnum",
                shadow = embossShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The contactless mark: four arcs opening to the right, each wider than the last. */
@Composable
private fun ContactlessMark() {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val color = Color.White.copy(alpha = 0.85f)
        val centre = Offset(size.width * 0.04f, size.height / 2)
        for (i in 1..4) {
            val radius = size.width * 0.2f * i
            drawArc(
                color,
                startAngle = -38f,
                sweepAngle = 76f,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2, radius * 2),
                style = stroke,
            )
        }
    }
}

@Composable
private fun CardBack(card: StoredCard, theme: BankBrandTheme, revealed: Boolean, bottomInset: Dp) {
    CardFace(theme, padding = 0.dp) {
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(20.dp))
            // Magnetic stripe with a subtle sheen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF1F1F1F), Color(0xFF0F0F0F)))),
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = AppSpacing.lg)) {
                Box(
                    Modifier
                        .weight(3f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                        .background(Color.White.copy(alpha = 0.85f))
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        "AUTHORIZED SIGNATURE",
                        style = TextStyle(color = Color.Black.copy(alpha = 0.35f), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                    )
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (card.hasCvv) {
                        Text(
                            if (revealed) card.cvv else "•".repeat(card.cvv.length),
                            style = TextStyle(
                                color = Color.Black.copy(alpha = 0.87f),
                                fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 1.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                            ),
                            maxLines = 1,
                        )
                    } else {
                        // Nothing is kept, so nothing is drawn as if it were: no dots.
                        Text(
                            "No CVV",
                            style = TextStyle(
                                color = Color.Black.copy(alpha = 0.45f),
                                fontWeight = FontWeight.SemiBold,
                                fontStyle = FontStyle.Italic,
                                fontSize = 10.sp,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.lg + bottomInset),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    theme.displayName,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, shadow = embossShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "CVV / CVC",
                    style = TextStyle(color = CardCaptionColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.6.sp, shadow = embossShadow),
                )
            }
        }
    }
}

@Composable
internal fun CardFace(
    theme: BankBrandTheme,
    padding: androidx.compose.ui.unit.Dp = AppSpacing.lg,
    /** More room under the print, for a card whose lower edge is covered (a deck's panel stands over it). */
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    // The one place a shadow is kept: card faces are physical objects, so a soft contact shadow reads as "card",
    // not "UI panel". It is painted, not an elevation shadow: the card turns in 3D (flip, tilt), and the system's
    // elevation shadow of a turned layer is drawn square and unrelated to the card's corners.
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawCardShadow() }
            .clip(AppRadius.rLg)
            .drawBehind {
                drawRect(theme.gradient.brush(size))
                drawBankPattern(theme.patternType, theme.accentColor)
                // A soft light from the top left, and the lower edge a touch deeper: a printed, slightly glossy card.
                drawRect(
                    Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.12f, -size.height * 0.1f),
                        radius = size.width * 0.85f,
                    ),
                )
                drawRect(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.2f)))
            }
            .drawWithContent {
                drawContent()
                // A hairline just inside the edge, so the card is cut out cleanly against any page colour. Twice as wide as
                // it shows: the clip takes the half that is outside.
                drawOutline(
                    AppRadius.rLg.createOutline(size, layoutDirection, this),
                    Color.White.copy(alpha = 0.18f),
                    style = Stroke(2.dp.toPx()),
                )
            }
            .padding(start = padding, top = padding, end = padding, bottom = padding + bottomInset),
    ) { content() }
}

/**
 * A card's size: a physical card's proportions, plus [extraHeightPx] - the strip of a deck's card that the panel covers, so
 * what shows above it is a whole card.
 */
private fun Modifier.cardProportions(extraHeightPx: Int): Modifier =
    if (extraHeightPx == 0) {
        aspectRatio(CARD_ASPECT)
    } else {
        layout { measurable, constraints ->
            val width = constraints.maxWidth
            val height = (width / CARD_ASPECT).roundToInt() + extraHeightPx
            val placeable = measurable.measure(Constraints.fixed(width, height))
            layout(width, height) { placeable.place(0, 0) }
        }
    }

/** The width a card's print is drawn for: a card of the list on a 360dp phone. A deck's narrower card shrinks the print to fit. */
private val PrintReferenceWidth = 328.dp

/** The type on a shrunken card is never smaller than this share of its size on a card of the list. */
private const val MinTypeScale = 0.9f

/**
 * What every pill on a card is laid on before its own colour: a faint dark, so a translucent-white pill (the usual kind) does not
 * lighten what is behind its words - on the lightest bank colours it took white text under 4.5:1 (see CardTextContrastTest).
 */
internal val CardBadgeUnderlay = Color.Black.copy(alpha = 0.18f)

@Composable
internal fun CardBadge(text: String, background: Color, border: Color, textColor: Color) {
    Box(
        Modifier
            .clip(AppRadius.rFull)
            .background(CardBadgeUnderlay)
            .background(background)
            .border(1.dp, border, AppRadius.rFull)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = TextStyle(color = textColor, fontWeight = FontWeight.Bold, fontSize = 9.sp, letterSpacing = 1.1.sp))
    }
}

/** EMV chip glyph with a gold or platinum/silver finish and circuit traces. */
@Composable
private fun EmvChip(finish: EmvChipFinish) {
    val colors = if (finish == EmvChipFinish.Gold) {
        listOf(Color(0xFFFBE8A6), Color(0xFFD4A017), Color(0xFFA67C00))
    } else {
        listOf(Color(0xFFE8E8E8), Color(0xFFB0B0B0), Color(0xFF888888))
    }
    val border = if (finish == EmvChipFinish.Gold) Color(0xFFECC865) else Color(0xFFCCCCCC)
    val shape = RoundedCornerShape(5.5.dp)
    Canvas(
        Modifier
            .size(38.dp, 28.dp)
            .shadow(2.dp, shape, ambientColor = Color(0x61000000), spotColor = Color(0x61000000))
            .clip(shape)
            .background(Brush.linearGradient(colors))
            .border(0.8.dp, border, shape),
    ) { drawChipLines() }
}

private fun DrawScope.drawChipLines() {
    val line = Color.Black.copy(alpha = 0.35f)
    val w = size.width
    val h = size.height
    drawLine(line, Offset(0f, h * 0.48f), Offset(w, h * 0.48f), strokeWidth = 1.dp.toPx())
    drawLine(line, Offset(w * 0.32f, 0f), Offset(w * 0.32f, h), strokeWidth = 1.dp.toPx())
    drawLine(line, Offset(w * 0.68f, 0f), Offset(w * 0.68f, h), strokeWidth = 1.dp.toPx())
    drawOval(
        Color.Black.copy(alpha = 0.18f),
        Offset(w * 0.32f, h * 0.30f),
        Size(w * 0.36f, h * 0.36f),
        style = Stroke(0.8.dp.toPx()),
    )
}

// ---- Network badges ---------------------------------------------------------------------

private val badgeShadow = Shadow(Color.Black.copy(alpha = 0.27f), Offset(0f, 1f), 3f)

/** A vector badge for the card network. */
@Composable
fun CardNetworkBadge(network: CardNetwork, height: androidx.compose.ui.unit.Dp) {
    val unit = height.value
    when (network) {
        CardNetwork.Visa -> Text(
            "VISA",
            style = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = (unit * 0.9f).sp, letterSpacing = 1.5.sp, shadow = badgeShadow),
        )
        CardNetwork.Mastercard -> Canvas(Modifier.size(height * 1.55f, height)) {
            val r = size.height / 2
            val left = Offset(r, r)
            val right = Offset(size.width - r, r)
            drawCircle(Color(0xFFEB001B), r, left)
            drawCircle(Color(0xFFF79E1B), r, right)
            val clip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(left, r)) }
            clipPath(clip) { drawCircle(Color(0xFFFF5F00), r, right) }
        }
        CardNetwork.Rupay -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "RuPay",
                style = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, fontSize = (unit * 0.85f).sp, letterSpacing = 0.3.sp, shadow = badgeShadow),
            )
            Spacer(Modifier.width(4.dp))
            Canvas(Modifier.size(height * 0.8f, height * 0.75f)) { drawRupayChevrons() }
        }
        CardNetwork.Amex -> Box(
            Modifier
                .shadow(2.dp, RoundedCornerShape(3.dp))
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF0070D2))
                .border(1.dp, Color.White, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
        ) { Text("AMEX", style = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 0.8.sp)) }
        CardNetwork.Discover -> Row(verticalAlignment = Alignment.CenterVertically) {
            val style = TextStyle(color = Color.White, fontWeight = FontWeight.Black, fontSize = (unit * 0.75f).sp, letterSpacing = 0.5.sp, shadow = badgeShadow)
            Text("DISC", style = style)
            Box(Modifier.padding(horizontal = 1.dp).size(height * 0.65f).clip(CircleShape).background(Color(0xFFE87722)))
            Text("VER", style = style)
        }
        CardNetwork.DinersClub -> Text(
            "Diners Club",
            style = TextStyle(color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = (unit * 0.65f).sp, letterSpacing = 0.3.sp, shadow = badgeShadow),
        )
        CardNetwork.Unknown -> Unit
    }
}

private fun DrawScope.drawRupayChevrons() {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(0f, 0f); lineTo(w * 0.45f, h / 2); lineTo(0f, h); lineTo(w * 0.25f, h); lineTo(w * 0.65f, h / 2); lineTo(w * 0.25f, 0f); close()
        },
        Color(0xFFF47920),
    )
    drawPath(
        Path().apply {
            moveTo(w * 0.35f, 0f); lineTo(w * 0.8f, h / 2); lineTo(w * 0.35f, h); lineTo(w * 0.6f, h); lineTo(w, h / 2); lineTo(w * 0.6f, 0f); close()
        },
        Color(0xFF00A651),
    )
}

/**
 * Standalone card face preview (without action bars or flip gestures), used in
 * forms for real-time visual feedback as the user types or selects their bank, and
 * on the front of the card stack on Home.
 */
@Composable
fun WalletCardFacePreview(
    card: StoredCard,
    modifier: Modifier = Modifier,
    revealed: Boolean = true,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
) {
    val density = LocalDensity.current
    val fixedDensity = remember(density) { Density(density.density, fontScale = 1f) }
    val theme = remember(card.bankName) { BankCardDesign.themeFor(card.bankName) }
    CompositionLocalProvider(LocalDensity provides fixedDensity) {
        Box(modifier.fillMaxWidth().aspectRatio(CARD_ASPECT)) { CardFront(card, theme, revealed, bankIconStyle) }
    }
}

/** A card's colours and pattern with nothing printed on it: all that shows of a card that lies behind another. */
@Composable
fun WalletCardPlate(card: StoredCard, modifier: Modifier = Modifier) {
    val theme = remember(card.bankName) { BankCardDesign.themeFor(card.bankName) }
    Box(modifier.fillMaxWidth().aspectRatio(CARD_ASPECT)) { CardFace(theme) {} }
}
