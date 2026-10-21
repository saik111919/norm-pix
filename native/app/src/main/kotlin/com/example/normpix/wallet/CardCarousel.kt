package com.example.normpix.wallet

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppMotion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.clipboard.CardField
import com.example.normpix.wallet.motion.CardTiltState
import kotlin.math.abs
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** How much of the page's width each side keeps free, for the neighbouring cards to show in. */
private val PeekPadding = 40.dp

/** The gap between two cards; with [PeekPadding] and [SIDE_SCALE] it leaves a sliver of each neighbour showing. */
private val CardGap = 8.dp

/** A card beside the one in front is drawn this big, and this faint, and grows into the front as it is swiped to. */
private const val SIDE_SCALE = 0.92f
private const val SIDE_ALPHA = 0.6f

/** On a wide window the carousel is no wider than this, centred: with [PeekPadding] either side, the card is the 440dp the card list caps its cards at. */
private val MaxCarouselWidth = 440.dp + PeekPadding * 2

/**
 * The saved cards as a carousel, the way card apps show them: the card in front in the middle, the ones either side peeking
 * in and a touch smaller, swiped across to change card (or tapped, to bring that one forward), with a row of dots for where
 * you are. Under it, what the card in front holds - bank and kind, number, cardholder, expiry and CVV - and its controls
 * ([CardActionBar]); both follow the card as it changes. Tapping the card in front turns it over, as everywhere else.
 *
 * What is hidden stays hidden: [revealed] says which cards show their whole number and CVV, here as on the cards. The controls
 * are always the bar of buttons: the swipe-to-reveal style ("Card actions") would fight the swipe that changes the card.
 */
@Composable
fun CardCarousel(
    cards: List<StoredCard>,
    revealed: List<String>,
    nfcGated: Boolean,
    bankIconStyle: BankIconStyle,
    tilt: CardTiltState?,
    onToggleReveal: (StoredCard) -> Unit,
    onShareNfc: (StoredCard) -> Unit,
    onEdit: (StoredCard) -> Unit,
    onDelete: (StoredCard) -> Unit,
    onCopy: (StoredCard, CardField?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { cards.size })
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    // A tick as each card settles in front, but not for the one it opened on, nor when the page moved only because the list did
    // (a card deleted, a bank filtered).
    val count by rememberUpdatedState(cards.size)
    LaunchedEffect(pagerState) {
        var seenCount = count
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            if (count == seenCount) haptics.selection()
            seenCount = count
        }
    }
    val front = cards[pagerState.currentPage.coerceIn(0, cards.lastIndex)]

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.widthIn(max = MaxCarouselWidth).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = PeekPadding, vertical = AppSpacing.sm),
                pageSpacing = CardGap,
                key = { cards[it].id },
            ) { page ->
                val card = cards[page]
                val inFront = page == pagerState.currentPage
                fun bringForward() {
                    scope.launch { pagerState.animateScrollToPage(page) }
                }
                Box(
                    Modifier
                        .graphicsLayer {
                            // 0 for the card in front, 1 once a whole page away: scale and fade follow the swipe.
                            val away = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                            val scale = 1f - (1f - SIDE_SCALE) * away
                            scaleX = scale
                            scaleY = scale
                            alpha = 1f - (1f - SIDE_ALPHA) * away
                        }
                        .then(
                            if (inFront) {
                                Modifier
                            } else {
                                // Beside the card in front, a card is only a way to it: nothing to turn over, and none of the
                                // card's own actions (delete, copy ...) for a screen reader - it says which card, and brings it forward.
                                Modifier.clearAndSetSemantics {
                                    val kind = if (card.kind == CardKind.Credit) "credit" else "debit"
                                    contentDescription = "${card.bankName} $kind card, ending in ${card.last4}, card ${page + 1} of ${cards.size}"
                                    role = Role.Button
                                    onClick(label = "Show this card") {
                                        bringForward()
                                        true
                                    }
                                }
                            },
                        ),
                ) {
                    WalletCardView(
                        card = card,
                        revealed = card.id in revealed,
                        // The card alone: its controls are the bar under the carousel, for whichever card is in front.
                        actionStyle = null,
                        nfcGated = nfcGated,
                        bankIconStyle = bankIconStyle,
                        onToggleReveal = { onToggleReveal(card) },
                        onShareNfc = { onShareNfc(card) },
                        onEdit = { onEdit(card) },
                        onDelete = { onDelete(card) },
                        tilt = tilt.takeIf { inFront },
                        onCopy = { field -> onCopy(card, field) },
                    )
                    if (!inFront) {
                        // A card at the side is a way to it, not a card to turn over: a tap brings it to the front.
                        Box(
                            Modifier
                                .matchParentSize()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = ::bringForward),
                        )
                    }
                }
            }
        }
        if (cards.size > 1) {
            Spacer(Modifier.height(AppSpacing.sm))
            CarouselDots(count = cards.size, current = pagerState.currentPage.coerceIn(0, cards.lastIndex))
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Column(Modifier.widthIn(max = MaxCarouselWidth).fillMaxWidth().padding(horizontal = AppSpacing.lg)) {
            Crossfade(
                targetState = front,
                animationSpec = tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard),
                label = "carousel_details",
            ) { card ->
                CarouselDetails(card, revealed = card.id in revealed)
            }
            Spacer(Modifier.height(AppSpacing.md))
            CardActionBar(
                revealed = front.id in revealed,
                nfcGated = nfcGated,
                onToggleReveal = { onToggleReveal(front) },
                onEdit = { onEdit(front) },
                onDelete = { onDelete(front) },
                onShareNfc = { onShareNfc(front) },
                onCopy = { onCopy(front, null) },
            )
        }
    }
}

/** One dot a card, the one in front a longer pill in the primary colour. Spoken as the place, "Card 2 of 3". */
@Composable
private fun CarouselDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Card ${current + 1} of $count" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scheme = MaterialTheme.colorScheme
        repeat(count) { index ->
            val selected = index == current
            val width by animateDpAsState(
                if (selected) 20.dp else 8.dp,
                tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard),
                label = "carousel_dot_width",
            )
            val color by animateColorAsState(
                if (selected) scheme.primary else scheme.outlineVariant,
                tween(AppMotion.duration(AppMotion.SHORT_MS), easing = AppMotion.standard),
                label = "carousel_dot_color",
            )
            Box(Modifier.height(8.dp).width(width).clip(CircleShape).background(color))
        }
    }
}

/** What a card holds, under the carousel: its bank and kind as a heading, then each detail, hidden until the card is revealed. */
@Composable
private fun CarouselDetails(card: StoredCard, revealed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Text(
            card.bankName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = AppSpacing.xs).semantics { heading() }.politeLiveRegion(),
        )
        val kind = if (card.kind == CardKind.Credit) "Credit card" else "Debit card"
        val network = networkLabel(CardNetwork.fromNumber(card.cardNumber))
        Text(
            listOfNotNull(kind, network, "Ending in ${card.last4}").joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.xs),
        )
        Spacer(Modifier.height(AppSpacing.md))
        ListSection {
            item {
                ListRow(
                    headline = if (revealed) groupDigits(card.cardNumber) else maskedNumber(card),
                    supporting = "Card number",
                )
            }
            item { ListRow(headline = card.cardholderName, supporting = "Cardholder") }
            item { ListRow(headline = "${card.expiryMonth}/${card.expiryYear}", supporting = "Expires") }
            // A card kept without a CVV has none to show: no row of dots for it.
            if (card.hasCvv) {
                item { ListRow(headline = if (revealed) card.cvv else "•".repeat(card.cvv.length), supporting = "CVV") }
            }
        }
    }
}

/** The number with everything but the last four digits as dots, in the groups of four the card prints it in. */
private fun maskedNumber(card: StoredCard): String =
    groupDigits("•".repeat((card.cardNumber.length - 4).coerceAtLeast(0)) + card.last4)

private fun networkLabel(network: CardNetwork): String? = when (network) {
    CardNetwork.Visa -> "Visa"
    CardNetwork.Mastercard -> "Mastercard"
    CardNetwork.Rupay -> "RuPay"
    CardNetwork.Amex -> "American Express"
    CardNetwork.Discover -> "Discover"
    CardNetwork.DinersClub -> "Diners Club"
    CardNetwork.Unknown -> null
}
