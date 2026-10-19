package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.example.normpix.finance.domain.NUMBER_START_LENGTH
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardNumberTransformation
import com.example.normpix.wallet.StoredCard
import com.example.normpix.wallet.groupDigits

/** What a card account's number field says under it, always: the number typed there is never kept whole. */
internal const val CARD_NUMBER_KEPT_NOTE = "Only the first $NUMBER_START_LENGTH and last $NUMBER_ENDING_LENGTH digits are kept, to recognise this card on receipts"

/** The shortest and longest card numbers there are (a few Maestro cards have 12, some UnionPay cards 19). */
private const val MIN_CARD_NUMBER_DIGITS = 12
private const val MAX_CARD_NUMBER_DIGITS = 19

/**
 * What was typed in a card account's number field, read as the digits worth keeping. Only the start and the end of a number
 * ever leave this: [Full] holds the first [NUMBER_START_LENGTH] and last [NUMBER_ENDING_LENGTH] digits and nothing between
 * them, so the whole number can't be saved by accident further along.
 */
internal sealed interface CardNumberEntry {
    /** Nothing typed. */
    data object None : CardNumberEntry

    /** Just the last [NUMBER_ENDING_LENGTH] digits, as an account could always be given. */
    data class Ending(val ending: String) : CardNumberEntry

    /** A whole number: its start and its end. [checksumOk]: it passes the Luhn check every card number does. */
    data class Full(val start: String, val ending: String, val checksumOk: Boolean) : CardNumberEntry

    /** Digits that are neither an ending nor a whole number (5 to 11, or 1 to 3). */
    data object Unusable : CardNumberEntry
}

/** Reads [typed] (spaces and anything but digits are ignored) as a [CardNumberEntry]. */
internal fun readCardNumber(typed: String): CardNumberEntry {
    val digits = typed.filter { it in '0'..'9' }
    return when {
        digits.isEmpty() -> CardNumberEntry.None
        digits.length == NUMBER_ENDING_LENGTH -> CardNumberEntry.Ending(digits)
        digits.length in MIN_CARD_NUMBER_DIGITS..MAX_CARD_NUMBER_DIGITS ->
            CardNumberEntry.Full(digits.take(NUMBER_START_LENGTH), digits.takeLast(NUMBER_ENDING_LENGTH), luhnOk(digits))
        else -> CardNumberEntry.Unusable
    }
}

/** The Luhn check digit test every payment card number passes; a failing number is most likely a mistyped one. */
internal fun luhnOk(digits: String): Boolean {
    if (digits.length < 2 || digits.any { it !in '0'..'9' }) return false
    var sum = 0
    for ((i, c) in digits.reversed().withIndex()) {
        var d = c - '0'
        if (i % 2 == 1) {
            d *= 2
            if (d > 9) d -= 9
        }
        sum += d
    }
    return sum % 10 == 0
}

/** What is kept of a card's number, drawn like a card shows it: "4111 11•• •••• 1111", or "•••• •••• •••• 1111" with no start. */
internal fun keptNumberMask(start: String?, ending: String, length: Int = 16): String {
    val head = start.orEmpty()
    val middle = "•".repeat((length - head.length - ending.length).coerceAtLeast(0))
    return groupDigits(head + middle + ending)
}

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
    /** A card account's number as typed now, up to 19 digits; it lives in the form only and is cut down to its start and end on saving. */
    cardNumber: String = "",
    onCardNumber: (String) -> Unit = {},
    /** The first [NUMBER_START_LENGTH] digits already kept for this account, if any. */
    keptStart: String? = null,
    /** Throws away the digits kept so far ([numberEnding] and [keptStart]). */
    onForgetNumber: () -> Unit = {},
) {
    if (kind == AccountKind.Card) {
        CardNumberField(
            cardNumber = cardNumber,
            onCardNumber = onCardNumber,
            keptStart = keptStart,
            keptEnding = numberEnding.takeIf { it.length == NUMBER_ENDING_LENGTH },
            onForget = onForgetNumber,
            error = numberError,
            modifier = modifier,
        )
    } else {
        OutlinedTextField(
            value = numberEnding,
            onValueChange = { entered -> onNumberEnding(entered.filter { it in '0'..'9' }.take(NUMBER_ENDING_LENGTH)) },
            modifier = modifier.fillMaxWidth(),
            label = { Text("Account number ends with (optional)") },
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
    }
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

/**
 * A card account's number: typed whole (grouped in fours like the Wallet's add-card form), or just its last four digits.
 * The number is only used to recognise the card on receipts, so only its first six and last four digits are kept - never the
 * whole of it - and the field says so. What is already kept is shown masked beneath the field, with a clear button.
 */
@Composable
private fun CardNumberField(
    cardNumber: String,
    onCardNumber: (String) -> Unit,
    keptStart: String?,
    keptEnding: String?,
    onForget: () -> Unit,
    error: String?,
    modifier: Modifier = Modifier,
) {
    val entry = readCardNumber(cardNumber)
    val checkHint = if (entry is CardNumberEntry.Full && !entry.checksumOk) "That doesn't look like a card number. Check the digits." else null
    val kept = if (cardNumber.isEmpty() && keptEnding != null) "Kept: ${keptNumberMask(keptStart, keptEnding)}. Type the number again to change it." else null
    OutlinedTextField(
        value = cardNumber,
        onValueChange = { entered -> onCardNumber(entered.filter { it in '0'..'9' }.take(MAX_CARD_NUMBER_DIGITS)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text("Card number (optional)") },
        placeholder = { Text("1234 5678 9012 3456") },
        trailingIcon = if (kept != null) {
            {
                IconButton(onClick = onForget) {
                    Icon(Icons.Outlined.Close, contentDescription = "Forget the kept digits")
                }
            }
        } else {
            null
        },
        supportingText = {
            Column {
                (error ?: checkHint ?: kept)?.let { Text(it) }
                Text(CARD_NUMBER_KEPT_NOTE)
            }
        },
        isError = error != null,
        visualTransformation = CardNumberTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next, autoCorrectEnabled = false),
        singleLine = true,
        shape = TonalFieldShape,
        colors = typedFieldColors(),
    )
}
