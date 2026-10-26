package com.example.normpix.wallet

import com.example.normpix.finance.domain.percentLabel
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.ui.label
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.DeckCarousel
import com.example.normpix.ui.components.DeckFact
import com.example.normpix.ui.components.DeckFacts
import com.example.normpix.ui.components.DeckDivider
import com.example.normpix.ui.components.DeckHero
import com.example.normpix.ui.theme.AppSpacing

/**
 * The accounts as a 3D carousel ([DeckCarousel]), as the saved cards are: the account in front facing you, the others turned
 * away either side. The cards say who: bank, kind, name. The panel they stand in says what the account holds - its balance and
 * what the card does not carry (what is left of its credit and when its bill is due, or else its share of your money and its
 * saved cards) - with a way to its history and to managing it. Nothing is said twice.
 *
 * The card in front is pressed as an account card is anywhere (a tap opens its history, a press and hold manages it); the
 * same two are the panel's buttons, which say so in words for those who would not find a gesture.
 */
@Composable
fun AccountCarousel(
    accounts: List<AccountCardData>,
    onOpen: (AccountCardData) -> Unit,
    onManage: (AccountCardData) -> Unit,
    modifier: Modifier = Modifier,
    bankIconStyle: BankIconStyle = BankIconStyle.Full,
) {
    DeckCarousel(
        items = accounts,
        itemKey = { it.id },
        tint = { AccountCardDesign.themeFor(it.bankName, it.kind).gradientColors.firstOrNull() },
        sideDescription = { account, index, count ->
            "${account.name}, ${account.kind.label}, balance ${if (account.hidden) "hidden" else account.balance}, account ${index + 1} of $count"
        },
        bringForwardLabel = "Show this account",
        modifier = modifier,
        card = { account, inFront, _ ->
            if (inFront) {
                AccountCardView(account, onOpen = { onOpen(account) }, onManage = { onManage(account) }, bankIconStyle = bankIconStyle, compact = true)
            } else {
                AccountCardFacePreview(account, bankIconStyle = bankIconStyle, compact = true)
            }
        },
        details = { account -> AccountDetails(account) },
        footer = { front ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The app's own button, as the Wallet's other actions are (Reveal under a card, Add card, Add account).
                FilledTonalButton(onClick = { onOpen(front) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("History", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(AppSpacing.sm))
                FilledTonalIconButton(onClick = { onManage(front) }) {
                    Icon(Icons.Outlined.Tune, contentDescription = "Manage account")
                }
            }
        },
    )
}

/**
 * What one account holds, as the text of the panel, in the same rows for every account (so the panel keeps its size as the
 * carousel moves). The card above says who it is - its bank, kind and name - so the panel does not: it says the balance, as
 * large as anything gets, with how that stands in words, then three facts the card does not carry. A card with a credit limit
 * says what is left, how much is used and when its bill is due; any other account says how much of what you hold is in it, and
 * how many cards are saved for its bank.
 *
 * When their labels would not fit side by side (large text, a narrow phone) the facts stand one under another, each the
 * panel's whole width, rather than three narrow columns in which "Credit left" is "Credit" and "₹32,712.03" is "₹32,7…".
 */
@Composable
private fun AccountDetails(account: AccountCardData) {
    val scheme = MaterialTheme.colorScheme
    val owing = account.standing == "Owed" || account.standing == "Overdrawn"
    DeckHero(
        label = if (account.standing != null) "Balance · ${account.standing}" else "Balance",
        value = account.balance,
        valueColor = if (owing) scheme.error else Color.Unspecified,
        // Hidden amounts are dots on the screen; a screen reader says that they are hidden instead of reading the dots out.
        valueDescription = if (account.hidden) "hidden" else null,
    )
    DeckDivider()

    val used = account.creditUsed
    // Each fact with the share of the row it takes when they sit side by side.
    val facts = if (account.credit != null && used != null) {
        listOf(
            DeckFact("Credit left", account.credit.removeSuffix(" left"), 1.4f, valueColor = if (account.overLimit) scheme.error else Color.Unspecified),
            DeckFact("Used", percentLabel(used.toDouble()), 0.8f),
            DeckFact(
                "Bill",
                account.due?.removePrefix("Due ")?.replaceFirstChar(Char::uppercase) ?: "None due",
                1.2f,
                valueColor = if (account.dueSoon) scheme.error else Color.Unspecified,
            ),
        )
    } else {
        buildList {
            // Nothing to say as a figure for an account that is owed, or while amounts are hidden: said in words, not as a dash.
            add(
                DeckFact(
                    "Of your money",
                    account.share ?: "—",
                    valueDescription = if (account.share != null) null else if (account.hidden) "hidden" else "not applicable",
                ),
            )
            if (account.bankName != null) add(DeckFact("Saved cards", if (account.cards > 0) account.cards.toString() else "None"))
        }
    }
    DeckFacts(facts)
}
