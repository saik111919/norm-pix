package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.data.LedgerCheckResult
import com.example.normpix.finance.domain.CardBillExpense
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.SelfTransferPair
import com.example.normpix.finance.ui.FinanceActions
import com.example.normpix.finance.ui.FinanceController
import com.example.normpix.finance.ui.TransactionTile
import com.example.normpix.finance.ui.offerUndo
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.finance.ui.shortDate
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlinx.coroutines.CoroutineScope

/*
 * Settings > Data & backup > Check and recalculate: the way to ask for the numbers to be worked out again from scratch when
 * something looks off. Under a loading sheet - one that can't be cancelled half way, since it changes data - it reads the whole
 * ledger, puts right what has one right answer, records repeating payments that were due and works every balance and total out
 * again (FinanceController.checkAndRepair). Then a sheet says what it read, what it put right (with Undo), and what is left for
 * the user to look at - each with the entries it is about, one tap from their details or from the fix.
 */

/** What the user picked in the results sheet. */
private sealed interface CheckChoice {
    data class Open(val transaction: FinanceTransaction) : CheckChoice

    data class Combine(val pair: SelfTransferPair) : CheckChoice

    data class CardBill(val bill: CardBillExpense) : CheckChoice

    data object Again : CheckChoice
}

/** How many entries of one kind of finding the sheet lists before saying how many more there are. */
private const val SHOWN_PER_FINDING = 5

/** Runs the check, shows what it found, and does what the user picks there; each fix can be undone for a few moments. */
internal suspend fun runLedgerCheck(
    finance: FinanceController,
    overlays: AppOverlays,
    snackbars: AppSnackbars,
    actions: FinanceActions,
    scope: CoroutineScope,
) {
    while (true) {
        val (result, undo) = overlays.loading(
            title = "Checking your data",
            message = "Reading every account, transaction, budget and repeating payment, then working out balances and totals " +
                "again. This takes a moment and can't be stopped half way.",
            cancellable = false,
        ) { finance.checkAndRepair() } ?: return
        val choice = overlays.sheet<CheckChoice>(expandable = true) { done -> LedgerCheckSheet(result, onChoice = done) }
        if (undo != null) {
            val fixed = result.issues.repairCount + result.recordedRepeats
            snackbars.offerUndo(if (fixed == 1) "1 thing put right" else "$fixed things put right", finance, undo, scope)
        }
        when (choice) {
            null -> return
            CheckChoice.Again -> continue
            is CheckChoice.Open -> {
                actions.showDetails(choice.transaction)
                return
            }
            is CheckChoice.Combine -> {
                val money = finance.state.value.money
                val e = choice.pair.expense
                val from = finance.state.value.account(e.accountId)?.name ?: "one account"
                val to = finance.state.value.account(choice.pair.income.accountId)?.name ?: "another"
                val confirmed = overlays.confirm(
                    title = "Record as one transfer?",
                    message = "The ${money.format(e.amount)} expense from $from and the income of the same into $to become one " +
                        "transfer between them. It's no longer counted as spending or income, and both balances stay as they are.",
                    confirmLabel = "Make it a transfer",
                    icon = Icons.Outlined.SwapHoriz,
                )
                if (!confirmed) return
                val done = runCatching { finance.combineSelfTransfer(choice.pair) }.getOrNull()
                if (done == null) snackbars.show("Couldn't change them. Nothing was changed.", SnackbarKind.Error) else snackbars.offerUndo("Recorded as a transfer", finance, done, scope)
                return
            }
            is CheckChoice.CardBill -> {
                val money = finance.state.value.money
                val confirmed = overlays.confirm(
                    title = "Record as a card payment?",
                    message = "The ${money.format(choice.bill.expense.amount)} expense becomes a payment onto ${choice.bill.card.name}: " +
                        "what is owed on the card comes down, and it isn't counted as spending twice.",
                    confirmLabel = "Record as card payment",
                    icon = Icons.Outlined.CreditCard,
                )
                if (!confirmed) return
                val done = runCatching { finance.recordCardBill(choice.bill) }.getOrNull()
                if (done == null) snackbars.show("Couldn't change it. Nothing was changed.", SnackbarKind.Error) else snackbars.offerUndo("Recorded as a card payment", finance, done, scope)
                return
            }
        }
    }
}

@Composable
private fun LedgerCheckSheet(result: LedgerCheckResult, onChoice: (CheckChoice?) -> Unit) {
    val finance = rememberFinanceState()
    val money = finance.money
    val issues = result.issues
    val fixed = issues.repairCount + result.recordedRepeats
    val findings = issues.findingCount
    val success = StatusTone.Success.resolve()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xl),
    ) {
        SheetHeader(
            title = when {
                findings == 0 && fixed == 0 -> "Everything adds up"
                findings == 0 -> "Put right, and it all adds up"
                else -> if (findings == 1) "1 thing to look at" else "$findings things to look at"
            },
            subtitle = "Checked ${count(result.transactions, "transaction")}, ${count(result.accounts, "account")}, " +
                "${count(result.categories, "category", "categories")} and ${count(result.rules, "repeating payment")}. " +
                "Every balance and total was worked out again from them.",
        )
        Spacer(Modifier.height(AppSpacing.lg))

        if (fixed > 0) {
            ListSection(title = "Put right") {
                for (loose in issues.looseSubCategories) item {
                    ListRow(
                        headline = "${loose.category.name} is a category of its own now",
                        supporting = "It was a sub-category, but ${loose.reason}.",
                        leading = { LeadingIcon(Icons.Outlined.Category, background = success.container, foreground = success.onContainer) },
                    )
                }
                if (issues.incomeBudgets.isNotEmpty()) item {
                    ListRow(
                        headline = "Removed ${count(issues.incomeBudgets.size, "budget")} on income categories",
                        supporting = "A budget limits spending: one on income counted toward nothing.",
                        leading = { LeadingIcon(Icons.Outlined.FactCheck, background = success.container, foreground = success.onContainer) },
                    )
                }
                for (catchUp in issues.ruleCatchUps) item {
                    ListRow(
                        headline = "${catchUp.rule.payee ?: "A repeating payment"} goes on from ${shortDate(catchUp.nextDate)}",
                        supporting = "Its next date was one it had already recorded, so it would have been counted twice.",
                        leading = { LeadingIcon(Icons.Outlined.Schedule, background = success.container, foreground = success.onContainer) },
                    )
                }
                if (issues.staleDefaultAccount) item {
                    ListRow(
                        headline = "The default account is chosen afresh",
                        supporting = "The one set was archived or gone; new entries start with the first active account.",
                        leading = { LeadingIcon(Icons.Outlined.AccountBalanceWallet, background = success.container, foreground = success.onContainer) },
                    )
                }
                if (result.recordedRepeats > 0) item {
                    ListRow(
                        headline = "Recorded ${count(result.recordedRepeats, "repeating payment")} that were due",
                        leading = { LeadingIcon(Icons.Outlined.Schedule, background = success.container, foreground = success.onContainer) },
                    )
                }
            }
        }

        if (issues.selfTransfers.isNotEmpty()) {
            ListSection(
                title = "Money moved between your accounts?",
                footer = "An expense from one account and income of the same into another, within a day: if it was one transfer, " +
                    "it's counted as spending and as income. Make it a transfer to count it as neither.",
            ) {
                for (pair in issues.selfTransfers.take(SHOWN_PER_FINDING)) {
                    item { TransactionTile(pair.expense, onClick = { onChoice(CheckChoice.Open(pair.expense)) }, showDate = true) }
                    item {
                        TextButton(onClick = { onChoice(CheckChoice.Combine(pair)) }, modifier = Modifier.padding(start = AppSpacing.sm)) {
                            Text("Make it a transfer to ${finance.account(pair.income.accountId)?.name ?: "the other account"}")
                        }
                    }
                }
                more(issues.selfTransfers.size)
            }
        }
        if (issues.cardBills.isNotEmpty()) {
            ListSection(
                title = "Card bills counted as spending",
                footer = "Paying a card's bill moves money onto the card; what was bought on it was counted already. Recorded as an " +
                    "expense, it's counted twice and the card goes on showing the bill as owed.",
            ) {
                for (bill in issues.cardBills.take(SHOWN_PER_FINDING)) {
                    item { TransactionTile(bill.expense, onClick = { onChoice(CheckChoice.Open(bill.expense)) }, showDate = true) }
                    item {
                        TextButton(onClick = { onChoice(CheckChoice.CardBill(bill)) }, modifier = Modifier.padding(start = AppSpacing.sm)) {
                            Text("Record as a payment onto ${bill.card.name}")
                        }
                    }
                }
                more(issues.cardBills.size)
            }
        }
        if (issues.duplicates.isNotEmpty()) {
            ListSection(
                title = "Entered twice?",
                footer = "The same amount, to the same payee, from the same account on the same day. Open one to delete it if it's a repeat.",
            ) {
                for (group in issues.duplicates.take(SHOWN_PER_FINDING)) {
                    item { TransactionTile(group.first(), onClick = { onChoice(CheckChoice.Open(group.first())) }, showDate = true) }
                    item {
                        ListRow(
                            headline = "${group.size} like this",
                            leading = { LeadingIcon(Icons.Outlined.ContentCopy) },
                            onClick = { onChoice(CheckChoice.Open(group.last())) },
                        )
                    }
                }
                more(issues.duplicates.size)
            }
        }
        if (issues.upcoming.isNotEmpty()) {
            ListSection(
                title = "Dated after today",
                footer = "These count from their own day, not before: they aren't in balances, budgets or what was spent yet. " +
                    "If one is dated wrong, open it to change its date.",
            ) {
                for (t in issues.upcoming.take(SHOWN_PER_FINDING)) item {
                    TransactionTile(t, onClick = { onChoice(CheckChoice.Open(t)) }, showDate = true)
                }
                more(issues.upcoming.size)
            }
        }
        if (issues.wrongCategory.isNotEmpty()) {
            ListSection(
                title = "In a category of the other kind",
                footer = "Income filed under a spending category, or spending under an income one: it shows in the wrong totals. Open one to choose its category.",
            ) {
                for (t in issues.wrongCategory.take(SHOWN_PER_FINDING)) item {
                    TransactionTile(t, onClick = { onChoice(CheckChoice.Open(t)) }, showDate = true)
                }
                more(issues.wrongCategory.size)
            }
        }
        if (issues.archivedBalances.isNotEmpty()) {
            ListSection(
                title = "Archived accounts with money in them",
                footer = "An archived account still counts in your total balance. Bring it back from the Wallet to move what's left, or record it as spent.",
            ) {
                for (held in issues.archivedBalances) item {
                    ListRow(
                        headline = held.account.name,
                        supporting = "${if (held.balance < 0) "Owes" else "Holds"} ${money.format(kotlin.math.abs(held.balance))}",
                        leading = { LeadingIcon(Icons.Outlined.AccountBalanceWallet) },
                    )
                }
            }
        }
        if (findings == 0) {
            ListSection {
                item {
                    ListRow(
                        headline = if (fixed == 0) "Nothing needed putting right" else "Nothing else to look at",
                        supporting = "Balances, budgets and totals are worked out from every entry each time they are shown.",
                        leading = { LeadingIcon(Icons.Rounded.CheckCircle, background = success.container, foreground = success.onContainer) },
                    )
                }
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))
        Button(onClick = { onChoice(null) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
        TextButton(onClick = { onChoice(CheckChoice.Again) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Text("Check again")
        }
    }
}

/** "… and 3 more", under a finding listing only its first few. */
private fun com.example.normpix.ui.components.ListSectionScope.more(total: Int) {
    if (total > SHOWN_PER_FINDING) item {
        Text(
            "and ${total - SHOWN_PER_FINDING} more",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
        )
    }
}

private fun count(n: Int, one: String, many: String = "${one}s"): String = "$n ${if (n == 1) one else many}"
