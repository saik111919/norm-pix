package com.example.normpix.wallet

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.DeckStack
import com.example.normpix.ui.components.DeckStackState
import com.example.normpix.ui.theme.AppRadius

/**
 * The saved cards as a stack: the front one printed in full, the others a hair of colour above it, each a little
 * smaller. Tapping the front card calls [onOpen]; swiping it sends it to the back (or, the other way, brings the last one
 * forward), and tapping a card behind brings that one forward (see [DeckStack]).
 *
 * Only the front card is printed. The ones behind are bare colour plates: a card's print starts further down than the
 * strip of a card behind shows, so nothing of it would be seen anyway - and the card number stays one card's worth.
 */
@Composable
fun CardStack(
    cards: List<StoredCard>,
    onOpen: (StoredCard) -> Unit,
    state: DeckStackState,
    modifier: Modifier = Modifier,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
) {
    DeckStack(
        items = cards,
        itemKey = { it.id },
        shape = AppRadius.rLg,
        description = { card, index, count ->
            val kind = if (card.kind == CardKind.Credit) "credit" else "debit"
            val place = if (count > 1) ", card ${index + 1} of $count" else ""
            "${card.bankName} $kind card, ending in ${card.last4}, expires ${card.expiryMonth}/${card.expiryYear}, ${card.cardholderName}$place"
        },
        onOpen = onOpen,
        state = state,
        modifier = modifier,
        plate = { WalletCardPlate(it) },
        face = { WalletCardFacePreview(it, revealed = false, bankIconStyle = bankIconStyle) },
    )
}
