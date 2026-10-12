package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.NUMBER_ENDING_LENGTH
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.StoredCard

/** "HDFC Bank · Credit •••• 4455": a Wallet card as the account form offers it, by what the Wallet shows of it - never the whole number. */
internal fun walletCardLabel(card: StoredCard): String =
    "${card.bankName.trim().ifEmpty { "Card" }} · ${card.kind.label} •••• ${card.last4}"

/**
 * The Wallet cards an account may be linked to: every card that is not already the card of another (active) account. A
 * card belongs to one account at most; [editing] keeps its own.
 */
internal fun linkableCards(cards: List<StoredCard>, accounts: List<Account>, editing: Account?): List<StoredCard> =
    cards.filter { card -> accounts.none { !it.archived && it.id != editing?.id && it.cardId == card.id } }

/**
 * What tells a shared receipt which of the user's accounts it is about: the last digits of the account's or card's number,
 * which payment apps and bank messages print ("XXXXXXXXXX3588"), and - where the account has a card in the Wallet - which
 * card that is. The user decides which is which; nothing is linked by itself.
 */
@Composable
fun AccountNumberFields(
    kind: AccountKind,
    numberEnding: String,
    onNumberEnding: (String) -> Unit,
    numberError: String?,
    /** The cards that can still be chosen (see [linkableCards]); empty hides the card choice. */
    cards: List<StoredCard>,
    cardId: String?,
    onCardId: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = if (kind == AccountKind.Card) "Card" else "Account"
    OutlinedTextField(
        value = numberEnding,
        onValueChange = { entered -> onNumberEnding(entered.filter { it in '0'..'9' }.take(NUMBER_ENDING_LENGTH)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text("$noun number ends with (optional)") },
        placeholder = { Text("Last $NUMBER_ENDING_LENGTH digits") },
        supportingText = {
            Text(numberError ?: "A shared payment screenshot or bank message that shows these digits is put on this account.")
        },
        isError = numberError != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        singleLine = true,
        shape = TonalFieldShape,
        colors = typedFieldColors(),
    )
    if (cards.isEmpty() && cardId == null) return
    Spacer(Modifier.height(AppSpacing.lg))
    val options = remember(cards) {
        buildList {
            add(ChoiceOption<String?>(key = null, label = "No card", icon = Icons.Outlined.LinkOff))
            for (card in cards) add(ChoiceOption<String?>(key = card.id, label = walletCardLabel(card), icon = Icons.Outlined.CreditCard))
        }
    }
    ChoiceDropdownField(
        label = "Card in your Wallet (optional)",
        options = options,
        selected = cardId,
        onSelect = onCardId,
        modifier = modifier,
    )
    Text(
        text = "Choose which card this account is. A receipt that shows the card's number is then put on this account.",
        modifier = Modifier.padding(top = AppSpacing.sm, start = AppSpacing.lg),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}
