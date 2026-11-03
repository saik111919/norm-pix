package com.example.normpix.wallet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contactless
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.wallet.motion.CardTiltState
import com.example.normpix.wallet.motion.ifFocused
import com.example.normpix.wallet.motion.tiltFocusCandidate
import kotlin.math.abs
import kotlinx.coroutines.launch

/** How much of each card behind the front one shows: its header strip, with its network, last digits and contactless mark. */
internal val FolderStep = 58.dp

/** How far the right-hand part of a card's top edge sits below its tab. */
private val FolderDrop = 16.dp

/** How wide a card's tab is, as a share of the card. */
private const val FolderTab = 0.64f

/** The card's corners, as its face has them (AppRadius.lg). */
private val FolderCorner = 16.dp

/**
 * The saved cards as a stack of folders:
 * every card behind the front one shows its header strip - a tab on the left with its network, its last four digits and the
 * contactless mark, the edge stepping down to the right of it so the card behind shows there - and the front card is printed in
 * full at the foot.
 *
 * Cards can be swiped horizontally left or right to cycle through the stack; tapping a card behind brings it to the front;
 * tapping the front card calls [onOpen].
 */
@Composable
fun CardFolderStack(
    cards: List<StoredCard>,
    onOpen: (StoredCard) -> Unit,
    modifier: Modifier = Modifier,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
    tilt: CardTiltState? = null,
) {
    if (cards.isEmpty()) return
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    // Which card is in front: the last by default, the one tapped or swiped once one is; kept across a rotation, and back to the last
    // when that card goes (deleted, filtered out).
    var frontId by rememberSaveable { mutableStateOf<String?>(null) }
    val front = cards.firstOrNull { it.id == frontId } ?: cards.last()
    val order = remember(cards, front.id) { cards.filter { it.id != front.id } + front }
    val spring = AppMotion.spatial<Dp>()
    val settle = AppMotion.spatial<Float>()

    val drag = remember(front.id) { Animatable(0f) }
    var widthPx by remember { mutableIntStateOf(0) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardHeight = maxWidth / CARD_ASPECT
        Box(Modifier.fillMaxWidth().height(FolderStep * (order.size - 1) + cardHeight)) {
            order.forEachIndexed { index, card ->
                key(card.id) {
                    val inFront = index == order.lastIndex
                    val top by animateDpAsState(FolderStep * index, spring, label = "folder_card_top")
                    val spoken = folderDescription(card, index, order.size)

                    val swipeModifier = if (inFront && order.size > 1) {
                        Modifier
                            .onSizeChanged { widthPx = it.width }
                            .graphicsLayer {
                                translationX = drag.value
                                rotationZ = if (widthPx > 0) (drag.value / widthPx) * 12f else 0f
                            }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    scope.launch { drag.snapTo(drag.value + delta) }
                                },
                                onDragStopped = { velocity ->
                                    val moved = drag.value
                                    val far = abs(moved) > widthPx * 0.22f || abs(velocity) > 800f
                                    val toward = if (abs(moved) > 1f) moved else velocity
                                    if (far && toward != 0f) {
                                        haptics.selection()
                                        val currentIdx = cards.indexOfFirst { it.id == front.id }
                                        if (currentIdx >= 0) {
                                            val nextIdx = if (toward < 0f) {
                                                (currentIdx + 1).mod(cards.size)
                                            } else {
                                                (currentIdx - 1 + cards.size).mod(cards.size)
                                            }
                                            frontId = cards[nextIdx].id
                                        }
                                    }
                                    scope.launch { drag.animateTo(0f, settle) }
                                },
                            )
                    } else {
                        Modifier
                    }

                    Box(
                        Modifier
                            .zIndex(index.toFloat())
                            .offset { IntOffset(0, top.roundToPx()) }
                            .fillMaxWidth()
                            .height(cardHeight)
                            .then(swipeModifier)
                            .clip(if (inFront) FrontShape else FolderTabShape)
                            .clickable(onClickLabel = if (inFront) "Open card" else "Bring to the front", role = Role.Button) {
                                if (inFront) {
                                    onOpen(card)
                                } else {
                                    haptics.selection()
                                    frontId = card.id
                                }
                            }
                            .clearAndSetSemantics {
                                contentDescription = spoken
                                role = Role.Button
                                onClick(label = if (inFront) "Open card" else "Bring to the front") {
                                    if (inFront) onOpen(card) else {
                                        haptics.selection()
                                        frontId = card.id
                                    }
                                    true
                                }
                                if (inFront && order.size > 1) {
                                    customActions = listOf(
                                        CustomAccessibilityAction("Show next card") {
                                            val currentIdx = cards.indexOfFirst { it.id == front.id }
                                            if (currentIdx >= 0) {
                                                frontId = cards[(currentIdx + 1).mod(cards.size)].id
                                            }
                                            true
                                        },
                                        CustomAccessibilityAction("Show previous card") {
                                            val currentIdx = cards.indexOfFirst { it.id == front.id }
                                            if (currentIdx >= 0) {
                                                frontId = cards[(currentIdx - 1 + cards.size).mod(cards.size)].id
                                            }
                                            true
                                        },
                                    )
                                }
                            },
                    ) {
                        if (inFront) {
                            val cardTilt = tilt?.ifFocused("folder-card-${card.id}")
                            Box(
                                Modifier
                                    .tiltFocusCandidate("folder-card-${card.id}", enabled = true)
                                    .graphicsLayer {
                                        if (cardTilt != null) {
                                            rotationX = -cardTilt.pitch
                                            rotationY = cardTilt.roll
                                        }
                                    }
                                    .then(if (cardTilt != null) Modifier.drawWithContent { drawContent(); drawSheen(cardTilt) } else Modifier)
                            ) {
                                WalletCardFacePreview(card, revealed = false, bankIconStyle = bankIconStyle)
                            }
                        } else {
                            FolderCard(card)
                        }
                    }
                }
            }
        }
    }
}

/** What a screen reader says of a card in the stack, and where it is. */
internal fun folderDescription(card: StoredCard, index: Int, count: Int): String {
    val kind = if (card.kind == CardKind.Credit) "credit" else "debit"
    val place = if (count > 1) if (index == count - 1) ", in front" else ", card ${index + 1} of $count, behind" else ""
    return "${card.bankName} $kind card, ending in ${card.last4}, expires ${card.expiryMonth}/${card.expiryYear}$place"
}

/**
 * A card behind the front one: its own colours, and on its tab the network, the last four digits and the contactless mark - all
 * that shows of it. Nothing else is printed, so a card's number stays one card's worth on screen.
 */
@Composable
private fun FolderCard(card: StoredCard) {
    val theme = remember(card.bankName, card.bankKey, card.cardName) { BankCardDesign.forCard(card) }
    val ink = theme.badgeTextColor
    Box {
        WalletCardPlate(card)
        Row(
            Modifier
                .fillMaxWidth(FolderTab)
                .padding(start = 18.dp, end = 12.dp)
                .height(FolderStep),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CardNetworkBadge(CardNetwork.fromNumber(card.cardNumber), 18.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                "•••• ${card.last4}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = ink.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.Contactless, contentDescription = null, tint = ink.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
    }
}

/** The front card: the card's own rounded corners all round, so nothing of its print is cut. */
private val FrontShape = androidx.compose.foundation.shape.RoundedCornerShape(FolderCorner)

/**
 * A card behind, as a folder: a tab on the start side ([FolderTab] of the width) at the top, the rest of the top edge [FolderDrop]
 * lower, joined by a smooth step; the card's own corners everywhere else. Mirrored for right-to-left.
 */
internal val FolderTabShape: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { FolderCorner.toPx() }
        val drop = with(density) { FolderDrop.toPx() }
        val step = with(density) { 22.dp.toPx() }
        val w = size.width
        val h = size.height
        val tabEnd = w * FolderTab
        val path = Path().apply {
            moveTo(0f, r)
            arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
            lineTo(tabEnd - step / 2, 0f)
            cubicTo(tabEnd, 0f, tabEnd, drop, tabEnd + step / 2, drop)
            lineTo(w - r, drop)
            arcTo(Rect(w - 2 * r, drop, w, drop + 2 * r), 270f, 90f, false)
            lineTo(w, h - r)
            arcTo(Rect(w - 2 * r, h - 2 * r, w, h), 0f, 90f, false)
            lineTo(r, h)
            arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
            close()
        }
        if (layoutDirection == LayoutDirection.Rtl) {
            path.transform(androidx.compose.ui.graphics.Matrix().apply { scale(-1f, 1f); translate(-w, 0f) })
        }
        return Outline.Generic(path)
    }
}
