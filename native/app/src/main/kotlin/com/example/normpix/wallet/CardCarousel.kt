package com.example.normpix.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.DeckCarousel
import com.example.normpix.ui.components.DeckDetail
import com.example.normpix.ui.components.DeckFact
import com.example.normpix.ui.components.DeckFacts
import com.example.normpix.ui.components.DeckDivider
import com.example.normpix.ui.components.DeckMarkerInset
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.clipboard.CardField
import com.example.normpix.wallet.motion.CardTiltState

/**
 * The saved cards as a 3D carousel ([DeckCarousel]) with the card in front's details in the panel the cards stand in.
 *
 * Tapping the card in front turns it over, as everywhere else, and it leans with the phone when card tilt is on. The panel
 * holds what the card holds - bank and kind, number, cardholder, expiry and, when the card keeps one, CVV - with the
 * controls ([CardActionBar]) at its foot.
 *
 * What is hidden stays hidden: [revealed] says which cards show their whole number and CVV, here as on the cards. A card
 * kept without a CVV has no CVV on its details, as on the card's sheet (Settings > Wallet & Cards > Save CVV off keeps none).
 * The controls are always the bar of buttons: the swipe-to-reveal style ("Card actions") would fight the swipe that changes the card.
 */
@Composable
fun CardCarousel(
    cards: List<StoredCard>,
    revealed: List<String>,
    nfcGated: Boolean,
    bankIconStyle: BankIconStyle,
    tilt: CardTiltState?,
    onToggleReveal: (StoredCard) -> Unit,
    /** Null while the NFC card exchange isn't offered (see `rememberNfcExchangeOffered`): the card has no Share then. */
    onShareNfc: ((StoredCard) -> Unit)?,
    onEdit: (StoredCard) -> Unit,
    onDelete: (StoredCard) -> Unit,
    onCopy: (StoredCard, CardField?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DeckCarousel(
        items = cards,
        itemKey = { it.id },
        tint = { BankCardDesign.themeFor(it.bankName).gradientColors.firstOrNull() },
        sideDescription = { card, index, count ->
            val kind = if (card.kind == CardKind.Credit) "credit" else "debit"
            "${card.bankName} $kind card, ending in ${card.last4}, card ${index + 1} of $count"
        },
        modifier = modifier,
        card = { card, inFront, space ->
            WalletCardView(
                card = card,
                revealed = card.id in revealed,
                // The card alone: its controls are the bar at the foot of the panel, for whichever card is in front.
                actionStyle = null,
                nfcGated = nfcGated,
                bankIconStyle = bankIconStyle,
                onToggleReveal = { onToggleReveal(card) },
                onShareNfc = onShareNfc?.let { share -> { share(card) } },
                onEdit = { onEdit(card) },
                onDelete = { onDelete(card) },
                tilt = tilt.takeIf { inFront },
                onCopy = { field -> onCopy(card, field) },
                deck = space,
            )
        },
        details = { card -> CardDetails(card, revealed = card.id in revealed) },
        footer = { front ->
            CardActionBar(
                revealed = front.id in revealed,
                nfcGated = nfcGated,
                onToggleReveal = { onToggleReveal(front) },
                onEdit = { onEdit(front) },
                onDelete = { onDelete(front) },
                onShareNfc = onShareNfc?.let { share -> { share(front) } },
                onCopy = { onCopy(front, null) },
                compact = true,
            )
        },
    )
}

/** What one saved card holds, as the text of the panel: hidden until the card is revealed. */
@Composable
private fun CardDetails(card: StoredCard, revealed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    // Room at the end of the first lines for the page marker.
    Column(Modifier.padding(end = DeckMarkerInset)) {
        Text(
            card.bankName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        val kind = if (card.kind == CardKind.Credit) "Credit card" else "Debit card"
        val network = networkLabel(CardNetwork.fromNumber(card.cardNumber))
        Text(
            listOfNotNull(kind, network, "Ending in ${card.last4}").joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(AppSpacing.md))
    DeckDetail("Card number", if (revealed) groupDigits(card.cardNumber) else maskedNumber(card), large = true)
    DeckDivider()
    DeckFacts(
        buildList {
            add(DeckFact("Cardholder", card.cardholderName, if (card.hasCvv) 1.5f else 2f))
            add(DeckFact("Expires", "${card.expiryMonth}/${card.expiryYear}"))
            // As on the card's sheet: a card kept without a CVV has none to show (Save CVV off keeps none on any card).
            if (card.hasCvv) add(DeckFact("CVV", if (revealed) card.cvv else "•".repeat(card.cvv.length)))
        },
    )
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
