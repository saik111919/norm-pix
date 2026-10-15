package com.example.normpix.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.AccountMark
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.RepeatSchedule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.ui.AsyncValue
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.FinanceState
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.TransactionTile
import com.example.normpix.finance.ui.describeRepeat
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.finance.ui.rememberFinanceQuery
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.navigation.AppDestination
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardVault
import com.example.normpix.wallet.StoredCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * A reminder can be about the thing that makes it matter: the expense to follow up, the income to expect, the repeating
 * payment due again, the account (a card's bill) or a saved card. The reminder stores only which one; what it says about it
 * - its name, its amount - is read fresh each time, so it never goes stale, and a reminder whose subject was deleted says so
 * instead of showing what it used to be.
 */

/** What a [ReminderLink] points at, ready to show. */
@Immutable
class ResolvedLink(
    val link: ReminderLink,
    val title: String,
    val detail: String?,
    val icon: ImageVector,
    /** The bank's name when this is a card, so its logo is drawn instead of [icon]. */
    val bankName: String? = null,
    /** What it pointed at is gone (the expense was deleted, the card removed). */
    val missing: Boolean = false,
    /** The transaction itself, for opening it. */
    val transaction: FinanceTransaction? = null,
) {
    /** "Netflix · Expense · ₹649 · 12 Oct": the title and the detail on one line. */
    val line: String get() = listOfNotNull(title, detail).joinToString(" · ")
}

private fun missing(link: ReminderLink, what: String) = ResolvedLink(
    link = link,
    title = "$what no longer exists",
    detail = null,
    icon = Icons.Outlined.LinkOff,
    missing = true,
)

/** The name a transaction goes by: its payee, else what it was spent on (or "Transfer"). */
internal fun transactionName(t: FinanceTransaction, state: FinanceState): String = t.payee
    ?: if (t.type == TransactionType.Transfer) "Transfer" else state.category(t.categoryId)?.let { state.categories.pathName(it) } ?: t.type.label

/** The line saying what a transaction was: "Expense · ₹649 · 12 Oct". */
fun transactionSummary(t: FinanceTransaction, state: FinanceState): String =
    "${t.type.label} · ${state.money.format(t.amount)} · ${shortDate(t.occurredAt)}"

/** The name a repeating payment goes by. */
fun recurringName(rule: RecurringRule, state: FinanceState): String =
    rule.payee ?: state.category(rule.categoryId)?.let { state.categories.pathName(it) } ?: "Repeating payment"

/** The line saying what a repeating payment is: "Every month · ₹649". */
fun recurringSummary(rule: RecurringRule, state: FinanceState): String =
    "${describeRepeat(RepeatSchedule(rule.frequency, rule.interval, rule.endDate))} · ${state.money.format(rule.amount)}"

private suspend fun resolveLink(link: ReminderLink, finance: FinanceController, state: FinanceState, vault: CardVault): ResolvedLink {
    val id = link.id.toLongOrNull()
    return when (link.kind) {
        ReminderLinkKind.Card -> {
            val card = vault.loadCards().firstOrNull { it.id == link.id } ?: return missing(link, "This card")
            ResolvedLink(link, "${card.bankName} •••• ${card.last4}", null, Icons.Outlined.CreditCard, bankName = card.bankName)
        }
        ReminderLinkKind.Account -> {
            val account: Account = state.account(id) ?: return missing(link, "This account")
            ResolvedLink(link, account.name, account.kind.label, account.kind.icon)
        }
        ReminderLinkKind.Transaction -> {
            val t = id?.let { finance.repository.transactionById(it) } ?: return missing(link, "This transaction")
            ResolvedLink(link, transactionName(t, state), transactionSummary(t, state), t.type.icon, transaction = t)
        }
        ReminderLinkKind.Recurring -> {
            val rule = finance.repository.recurringRules().firstOrNull { it.id == id } ?: return missing(link, "This repeating payment")
            ResolvedLink(link, recurringName(rule, state), recurringSummary(rule, state), Icons.Outlined.EventRepeat)
        }
    }
}

/**
 * What [link] points at, read fresh: null while it loads (and for no link). Reads again whenever the finance data changes,
 * so renaming the payee or changing the amount shows up here.
 */
@Composable
fun rememberResolvedLink(link: ReminderLink?): ResolvedLink? {
    val finance = LocalFinance.current
    // As screens show it: with amounts hidden while the user has hidden them.
    val state = rememberFinanceState()
    val vault = LocalAppContainer.current.vault
    val resolved by produceState<ResolvedLink?>(null, link, state.revision) {
        value = if (link == null) {
            null
        } else {
            try {
                resolveLink(link, finance, state, vault)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }
    }
    return if (link == null) null else resolved
}

/** Opens what a link points at: the expense's details, the repeating payment's editor, the account's history, the Wallet for a card. For screens (not sheets). */
@Composable
fun rememberOpenLink(): (ResolvedLink) -> Unit {
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    return remember(actions, navigator) {
        { resolved ->
            val id = resolved.link.id.toLongOrNull()
            when {
                resolved.missing -> Unit
                resolved.transaction != null -> actions.showDetails(resolved.transaction)
                resolved.link.kind == ReminderLinkKind.Recurring && id != null -> actions.editRecurring(id)
                resolved.link.kind == ReminderLinkKind.Account && id != null -> navigator.openAccountHistory(id)
                resolved.link.kind == ReminderLinkKind.Card -> navigator.go(AppDestination.Wallet)
            }
        }
    }
}

/** A resolved link as one small line - its icon (or the card's bank logo) and its words - for under a reminder's title. */
@Composable
fun ReminderLinkLine(resolved: ResolvedLink, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (resolved.missing) scheme.onSurfaceVariant.copy(alpha = 0.7f) else scheme.onSurfaceVariant
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (resolved.bankName != null) {
            BankGlyph(resolved.bankName, tint = tint, size = 14.dp)
        } else {
            Icon(resolved.icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(AppSpacing.xs))
        Text(resolved.line, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What the attach sheet hands back: [link] is the thing chosen, or null when the attachment is to be removed. */
class AttachChoice(val link: ReminderLink?)

private enum class AttachTab(val label: String, val icon: ImageVector) {
    // Short on purpose: four labels share one row, and the chosen one grows. "Activity" is where any expense, income or transfer is.
    Transaction("Activity", Icons.Outlined.ReceiptLong),
    Recurring("Repeating", Icons.Outlined.EventRepeat),
    Account("Account", Icons.Outlined.AccountBalanceWallet),
    Card("Card", Icons.Outlined.CreditCard),
}

private fun ReminderLink?.tab(): AttachTab = when (this?.kind) {
    ReminderLinkKind.Recurring -> AttachTab.Recurring
    ReminderLinkKind.Account -> AttachTab.Account
    ReminderLinkKind.Card -> AttachTab.Card
    else -> AttachTab.Transaction
}

/** Test tag of the attach sheet's scrolling list. */
const val ATTACH_LIST_TAG = "reminder_attach_list"

/**
 * "Attach to": choose what a reminder is about, one tab per kind of thing - a transaction (any expense, income or transfer,
 * found by search), a repeating payment, an account, or a saved card (or add one). A tap picks and closes. [current] is
 * marked, and can be removed. [onDone] gets the choice, or null when the sheet is dismissed.
 */
@Composable
fun ReminderAttachSheet(
    current: ReminderLink?,
    cards: List<StoredCard>,
    onAddCard: suspend () -> StoredCard?,
    onDone: (AttachChoice?) -> Unit,
) {
    val finance = com.example.normpix.finance.ui.rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(current.tab()) }
    var query by remember { mutableStateOf("") }
    val tabs = remember { AttachTab.entries.map { ChoiceOption(it, it.label, it.icon) } }

    fun pick(link: ReminderLink) = onDone(AttachChoice(link))

    @Composable
    fun TonalRow(selected: Boolean, content: @Composable () -> Unit) {
        Surface(
            shape = AppRadius.rMd,
            color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHigh,
            content = content,
        )
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.md)) {
            SheetHeader(title = "Attach to", subtitle = "What is this reminder about?")
            Spacer(Modifier.height(AppSpacing.md))
            ConnectedChoiceRow(tabs, selected = tab, onSelect = { tab = it })
            if (tab == AttachTab.Transaction) {
                Spacer(Modifier.height(AppSpacing.md))
                SearchField(value = query, onValueChange = { query = it }, placeholder = "Search transactions")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false).testTag(ATTACH_LIST_TAG),
            contentPadding = PaddingValues(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (tab) {
                AttachTab.Transaction -> transactionRows(query, current, ::pick) { selected, content -> TonalRow(selected, content) }
                AttachTab.Recurring -> recurringRows(current, ::pick) { selected, content -> TonalRow(selected, content) }
                AttachTab.Account -> {
                    val accounts = finance.accounts.filter { !it.archived || ReminderLink.account(it.id ?: -1) == current }
                    if (accounts.isEmpty()) {
                        item { EmptyNote("No accounts yet. Add one in Wallet.") }
                    }
                    items(accounts, key = { "account-${it.id}" }) { account ->
                        val link = ReminderLink.account(account.id!!)
                        TonalRow(selected = link == current) {
                            ListItem(
                                modifier = Modifier.clickable { pick(link) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { AccountMark(account, scheme.onSurfaceVariant, 28.dp) },
                                headlineContent = { Text(account.name) },
                                supportingContent = { Text(account.kind.label) },
                                trailingContent = if (link == current) ({ Icon(Icons.Rounded.Check, null, tint = scheme.primary) }) else null,
                            )
                        }
                    }
                }
                AttachTab.Card -> {
                    if (cards.isEmpty()) item { EmptyNote("No saved cards yet.") }
                    items(cards, key = { "card-${it.id}" }) { card ->
                        val link = ReminderLink.card(card.id)
                        TonalRow(selected = link == current) {
                            ListItem(
                                modifier = Modifier.clickable { pick(link) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { BankGlyph(card.bankName, tint = scheme.onSurfaceVariant, size = 28.dp) },
                                headlineContent = { Text("${card.bankName} •••• ${card.last4}") },
                                trailingContent = if (link == current) ({ Icon(Icons.Rounded.Check, null, tint = scheme.primary) }) else null,
                            )
                        }
                    }
                    item(key = "add-card") {
                        TonalRow(selected = false) {
                            ListItem(
                                modifier = Modifier.clickable { scope.launch { onAddCard()?.let { pick(ReminderLink.card(it.id)) } } },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { Icon(Icons.Rounded.Add, contentDescription = null, tint = scheme.primary) },
                                headlineContent = { Text("Add a card", color = scheme.primary) },
                            )
                        }
                    }
                }
            }
        }
        if (current != null) {
            TextButton(
                onClick = { onDone(AttachChoice(null)) },
                modifier = Modifier.padding(horizontal = AppSpacing.lg),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(AppSpacing.sm))
                Text("Remove attachment")
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(AppSpacing.lg),
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.transactionRows(
    query: String,
    current: ReminderLink?,
    pick: (ReminderLink) -> Unit,
    row: @Composable (selected: Boolean, content: @Composable () -> Unit) -> Unit,
) {
    item(key = "transactions") {
        val result by rememberFinanceQuery(query) {
            transactions(TransactionQuery(text = query.trim().takeIf { it.isNotEmpty() }), limit = 40).items
        }
        when (val found = result) {
            is AsyncValue.Data -> {
                if (found.value.isEmpty()) {
                    EmptyNote(if (query.isBlank()) "No transactions yet." else "No transaction matches “${query.trim()}”.")
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (t in found.value) {
                        val link = ReminderLink.transaction(t.id!!)
                        row(link == current) {
                            TransactionTile(t, onClick = { pick(link) }, showDate = true)
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.recurringRows(
    current: ReminderLink?,
    pick: (ReminderLink) -> Unit,
    row: @Composable (selected: Boolean, content: @Composable () -> Unit) -> Unit,
) {
    item(key = "rules") {
        val result by rememberFinanceQuery(Unit) { recurringRules() }
        val state = com.example.normpix.finance.ui.rememberFinanceState()
        val scheme = MaterialTheme.colorScheme
        when (val found = result) {
            is AsyncValue.Data -> {
                if (found.value.isEmpty()) EmptyNote("No repeating payments yet. Add one under Plans > Recurring, or repeat an expense when you enter it.")
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (rule in found.value) {
                        val link = ReminderLink.recurring(rule.id!!)
                        row(link == current) {
                            ListItem(
                                modifier = Modifier.clickable { pick(link) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                leadingContent = { Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = scheme.onSurfaceVariant) },
                                headlineContent = { Text(recurringName(rule, state), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(recurringSummary(rule, state)) },
                                trailingContent = if (link == current) ({ Icon(Icons.Rounded.Check, null, tint = scheme.primary) }) else null,
                            )
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

/**
 * A transaction told as a sentence, for the notes of a reminder about it: "Expense of ₹649 on 12 Oct from HDFC Savings",
 * "Income of ₹50,000 on 1 Oct into HDFC Savings", "Transfer of ₹2,000 on 5 Oct from Cash to HDFC Savings".
 */
fun transactionStory(t: FinanceTransaction, state: FinanceState): String {
    val account = state.account(t.accountId)?.name
    val to = state.account(t.toAccountId)?.name
    val where = when (t.type) {
        TransactionType.Expense -> account?.let { " from $it" }
        TransactionType.Income -> account?.let { " into $it" }
        TransactionType.Transfer -> if (account != null && to != null) " from $account to $to" else null
    }.orEmpty()
    return "${t.type.label} of ${state.money.format(t.amount)} on ${shortDate(t.occurredAt)}$where"
}
