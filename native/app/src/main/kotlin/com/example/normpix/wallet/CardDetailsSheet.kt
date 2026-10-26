package com.example.normpix.wallet

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.settings.WalletCardActionStyle
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.politeLiveRegion
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.clipboard.CardField
import com.example.normpix.wallet.motion.rememberCardTilt
import com.example.normpix.wallet.nfc.ExchangeTab

/**
 * One saved card in a sheet: the card itself (it turns over to show the CVV) with the controls Wallet gives it, and under
 * it each detail as a row - tap one to copy it. Number and CVV stay hidden until revealed, through the same NFC-tag step
 * as in Wallet ([CardReveal]); copying a row goes through it too, without showing anything.
 *
 * The card is looked up by [cardId] on every change of the card list, so an edit made from here shows at once, and the
 * sheet closes ([onDismiss]) when the card is deleted. The sheet sits above the navigation graph, so the caller's [flows]
 * are handed in rather than rebuilt here.
 */
@Composable
fun CardDetailsSheet(cardId: String, flows: CardFlows, onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val cardsState by container.cards.state.collectAsState()
    val card = (cardsState as? CardsState.Ready)?.cards?.firstOrNull { it.id == cardId }
    // Deleted from here, or from anywhere else: there is nothing left to show.
    LaunchedEffect(card == null) { if (card == null) onDismiss() }
    if (card == null) return

    val reveal = rememberCardReveal(flows)
    val revealed = card.id in reveal.revealed
    // Share is the NFC card exchange: offered only while it is (phone NFC on, and the app's switch for it).
    val nfcOffered = rememberNfcExchangeOffered()
    val tilt = rememberCardTilt(enabled = prefs.cardTiltEnabled)
    // A snackbar would sit under this sheet, so what a copy says, it says here.
    var notice by remember { mutableStateOf("") }
    var noticeShown by remember { mutableStateOf(false) }
    fun copy(field: CardField) = reveal.copy(card, field) { message ->
        notice = message
        noticeShown = true
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.xl),
    ) {
        SheetHeader(title = card.bankName, subtitle = "${card.kind.label} card ending ${card.last4}")
        Spacer(Modifier.height(AppSpacing.lg))
        WalletCardView(
            card = card,
            revealed = revealed,
            // Buttons, whatever Wallet is set to: a swipe on a card would fight the sheet's own drag.
            actionStyle = WalletCardActionStyle.Bar,
            nfcGated = prefs.nfcTagRevealEnabled,
            bankIconStyle = prefs.bankIconStyle,
            onToggleReveal = { reveal.toggle(card.id) },
            onShareNfc = if (nfcOffered) { { flows.openNfcExchange(ExchangeTab.Outlet, card) } } else null,
            onEdit = { flows.editCard(card) },
            onDelete = {
                reveal.revealed.remove(card.id)
                flows.deleteCard(card)
            },
            tilt = tilt,
            onCopy = { field -> if (field == null) reveal.copy(card, null) else copy(field) },
        )
        RevealVisibility(visible = noticeShown) {
            Text(
                notice,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.md).politeLiveRegion(),
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        ListSection {
            item {
                DetailRow(
                    label = CardField.Number.label,
                    value = if (revealed) groupDigits(card.cardNumber) else "•••• •••• •••• ${card.last4}",
                    spoken = if (revealed) groupDigits(card.cardNumber) else "ending in ${card.last4}",
                    onCopy = { copy(CardField.Number) },
                )
            }
            item {
                DetailRow(
                    label = CardField.Expiry.label,
                    value = "${card.expiryMonth}/${card.expiryYear}",
                    onCopy = { copy(CardField.Expiry) },
                )
            }
            // A card kept without a CVV has no row for one: nothing to show, nothing to copy.
            if (card.hasCvv) {
                item {
                    DetailRow(
                        label = CardField.Cvv.label,
                        value = if (revealed) card.cvv else "•".repeat(card.cvv.length),
                        spoken = if (revealed) card.cvv else "hidden",
                        onCopy = { copy(CardField.Cvv) },
                    )
                }
            }
            item {
                DetailRow(
                    label = CardField.Holder.label,
                    value = card.cardholderName,
                    onCopy = { copy(CardField.Holder) },
                )
            }
        }
    }
}

/** A detail of the card with its name above it; tapping the row copies it. */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
    spoken: String = value,
) {
    ListRow(
        headline = value,
        supporting = label,
        trailing = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onCopy,
        // Said in words, not as the row's dots and digits: "ending in 4242", "hidden".
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "$label, $spoken"
            role = Role.Button
            onClick(label = "Copies the ${label.lowercase()}") { onCopy(); true }
        },
    )
}
