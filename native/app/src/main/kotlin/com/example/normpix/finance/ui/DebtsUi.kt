package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.normpix.finance.domain.DebtEntry
import com.example.normpix.finance.domain.DebtKind
import com.example.normpix.finance.domain.DebtTotals
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.MAX_DEBT_NOTE
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.debtBalancePhrase
import com.example.normpix.finance.domain.debtCategoryFor
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.ConnectedButtonGroup
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.GroupButton
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Money lent and borrowed with people, on screen (owner, 2026-11-19: "Lent and borrowed with people ... settle up with one tap, which
 * records the transfer"). The rules are in `finance/domain/Debts.kt`; the actions (open the form, settle up, a record's sheet) in
 * `FinanceActions`.
 */

/** The test tag of a person's "Money between you" card. */
internal const val PERSON_DEBTS_TAG = "person_debts"

/**
 * What the People page says above the list when anyone owes anything: what is owed to the user and what the user owes, in the app's
 * two-panel banner. Nothing when everyone is settled.
 */
@Composable
internal fun DebtsBanner(totals: DebtTotals, people: Int, modifier: Modifier = Modifier) {
    if (totals.isEmpty) return
    val money = rememberFinanceState().money
    val owed = money.format(totals.owedToMe)
    val owe = money.format(totals.iOwe)
    SplitStatBanner(
        first = BannerFigure("Owed to you", owed),
        second = BannerFigure("You owe", owe),
        spoken = "Owed to you $owed. You owe $owe. Across $people ${if (people == 1) "person" else "people"}.",
        modifier = modifier,
    )
}

/** A person row's line about what is owed - "Owes you ₹500", "You owe them ₹200" - and its tone, or null when they are settled. */
@Composable
internal fun debtLine(balance: Long): Pair<String, androidx.compose.ui.graphics.Color>? {
    val money = rememberFinanceState().money
    return when {
        balance > 0 -> "Owes you ${money.format(balance)}" to StatusTone.Success.resolve().color
        balance < 0 -> "You owe them ${money.format(-balance)}" to StatusTone.Warning.resolve().color
        else -> null
    }
}

/**
 * On a person's page, under what was spent on them: what the user and [person] owe each other ("Ravi owes you ₹500", in words, not
 * colour alone), Settle up while anything is owed, "You lent" and "You borrowed" to record more, and every record, newest first - a tap
 * opens one (its transaction, Delete). An archived person's records are shown, but nothing new is added for them.
 */
@Composable
internal fun PersonDebtsCard(person: Person, modifier: Modifier = Modifier) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val scheme = MaterialTheme.colorScheme
    val id = person.id ?: return
    val balance = finance.debtBalances[id] ?: 0L
    val records = finance.debtsWith(id)
    val phrase = debtBalancePhrase(person.name, balance) { finance.money.format(it) }
    val tone = when {
        balance > 0 -> StatusTone.Success.resolve().color
        balance < 0 -> StatusTone.Warning.resolve().color
        else -> scheme.onSurface
    }

    Surface(
        color = scheme.surfaceContainer,
        shape = AppRadius.rXl,
        modifier = modifier.fillMaxWidth().padding(bottom = AppSpacing.md).testTag(PERSON_DEBTS_TAG),
    ) {
        Column(Modifier.padding(AppSpacing.lg)) {
            Text(
                "Money between you",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                if (balance == 0L && records.isEmpty()) "Nothing owed between you" else phrase,
                style = MaterialTheme.typography.titleLarge,
                color = tone,
            )
            if (!person.archived) {
                Spacer(Modifier.height(AppSpacing.md))
                if (balance != 0L) {
                    Button(
                        onClick = { actions.settleUp(person) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Rounded.Handshake, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text("Settle up")
                    }
                    Spacer(Modifier.height(AppSpacing.sm))
                }
                ConnectedButtonGroup(
                    buttons = listOf(
                        GroupButton("You lent", Icons.Rounded.ArrowUpward, scheme.surfaceContainerHighest, scheme.onSurface) {
                            actions.addDebt(person, DebtKind.Lent)
                        },
                        GroupButton("You borrowed", Icons.Rounded.ArrowDownward, scheme.surfaceContainerHighest, scheme.onSurface) {
                            actions.addDebt(person, DebtKind.Borrowed)
                        },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (records.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.md))
                for (record in records) DebtRecordRow(record, person) { actions.manageDebt(record, person) }
            }
        }
    }
}

/** One record on a person's card: which way the money went, the record in words, its date and note, the amount; a tap opens it. */
@Composable
private fun DebtRecordRow(record: DebtEntry, person: Person, onOpen: () -> Unit) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val amount = finance.money.format(record.amount)
    val headline = record.kind.phrase(person.name)
    val details = listOfNotNull(
        shortDate(record.occurredAt),
        record.note,
        if (record.transactionId != null) "in your accounts" else null,
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = "$headline $amount, $details"
                role = Role.Button
                onClick(label = "Open") { onOpen(); true }
            }
            .padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = CircleShape, color = scheme.surfaceContainerHighest, modifier = Modifier.size(36.dp)) {
            Icon(
                // Money out (lent, paid back) goes up; money in comes down - as the + menu's Expense and Income do.
                if (record.kind.moneyIn) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.padding(AppSpacing.sm),
            )
        }
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(headline, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(details, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Text(amount, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES))
    }
}

/**
 * Records money of [kind] with [person]: the amount (on the app's keypad, sums and all), the day, a note, and - on by default - the
 * account the money moved through, which also records it there as an expense or income in the kind's category ("Lent to someone" under
 * Money sent, "Loan repaid to me" under Money received...; any other can be picked). Off keeps only what is owed (half of a bill already
 * recorded). [initialAmount] fills the amount (Settle up: the whole balance, to be lowered for part of it). Gives back the record kept,
 * or null when closed without saving.
 */
@Composable
internal fun DebtFormSheet(person: Person, kind: DebtKind, initialAmount: Long?, onDone: (DebtEntry?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val decimals = finance.currency.decimals
    val type = if (kind.moneyIn) TransactionType.Income else TransactionType.Expense
    val role = type.role()

    var amount by remember { mutableStateOf(initialAmount?.let { minorToInputText(it, decimals) }.orEmpty()) }
    var occurredAt by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0)) }
    var note by remember { mutableStateOf("") }
    var record by remember { mutableStateOf(true) }
    var accountId by remember { mutableStateOf(finance.defaultAccount?.id ?: finance.activeAccounts.firstOrNull()?.id) }
    var categoryId by remember { mutableStateOf(debtCategoryFor(kind, finance.categories)?.id) }
    var pickingDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val initial = remember { listOf(amount, note, record, accountId, categoryId).joinToString("|") }
    val dirty = listOf(amount, note, record, accountId, categoryId).joinToString("|") != initial
    DiscardChangesGuard(dirty = dirty, saving = saving, isEditing = false, keptMessage = "")

    val categories = finance.categories.filter { it.type == CategoryType.forTransaction(type) && (!it.archived || it.id == categoryId) }
    val accounts = finance.activeAccounts
    // The amount is the first thing typed, so it opens on it - its keypad up - as a new expense does; Settle up's comes filled in, to read first.
    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (initialAmount == null) runCatching { amountFocus.requestFocus() } }

    fun save() {
        if (saving) return
        val parsed = parseMinorUnits(amount, decimals)
        formError = null
        amountError = if (parsed == null || parsed <= 0) "Enter an amount greater than zero" else null
        accountError = if (record && accountId == null) "Choose an account" else null
        categoryError = if (record && categoryId == null) "Choose a category" else null
        if (amountError != null || accountError != null || categoryError != null || parsed == null) return
        val now = LocalDateTime.now()
        val entry = DebtEntry(
            personId = person.id!!,
            kind = kind,
            amount = parsed,
            occurredAt = occurredAt,
            note = note.trim().takeIf { it.isNotEmpty() },
            createdAt = now,
            updatedAt = now,
        )
        val moved = if (!record) {
            null
        } else {
            FinanceTransaction(
                type = type,
                amount = parsed,
                accountId = accountId!!,
                categoryId = categoryId,
                occurredAt = occurredAt,
                // The person's name as the payee, so the entry reads as theirs in the history (it is not "spent on" them: lending is not).
                payee = person.name,
                note = note.trim().takeIf { it.isNotEmpty() },
                createdAt = now,
                updatedAt = now,
            )
        }
        saving = true
        scope.launch {
            try {
                val saved = controller.saveDebt(entry, moved)
                haptics.success()
                onDone(saved)
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = kind.phrase(person.name),
        saveLabel = "Save",
        onSave = ::save,
        saving = saving,
        errorText = formError,
        saveColors = ButtonDefaults.buttonColors(containerColor = role.color, contentColor = role.onColor),
    ) {
        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = role,
            errorText = amountError,
            modifier = Modifier.focusRequester(amountFocus),
        )
        Spacer(Modifier.height(AppSpacing.xxl))
        PickerFieldRow(first = PickerField("Date", shortDate(occurredAt)) { pickingDate = true })
        Spacer(Modifier.height(AppSpacing.lg))
        SwitchRow(
            title = if (kind.moneyIn) "Received into an account" else "Paid from an account",
            checked = record,
            onChange = {
                haptics.selection()
                record = it
            },
            subtitle = if (record) {
                "Also recorded there as ${if (kind.moneyIn) "income" else "an expense"}, so its balance follows."
            } else {
                "Only what is owed is kept - for a share of a bill you already recorded, say. No account changes."
            },
        )
        RevealVisibility(visible = record) {
            Column {
                Spacer(Modifier.height(AppSpacing.md))
                AccountSelector(
                    label = if (kind.moneyIn) "Into" else "Paid from",
                    accounts = accounts,
                    selected = accountId,
                    onSelect = {
                        accountId = it
                        accountError = null
                    },
                    errorText = accountError,
                )
                Spacer(Modifier.height(AppSpacing.lg))
                TransactionCategoryField(
                    categories = categories,
                    selected = categoryId,
                    onSelect = {
                        categoryId = it
                        categoryError = null
                    },
                    onCreate = null,
                    errorText = categoryError,
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = note,
            onValueChange = { if (it.length <= MAX_DEBT_NOTE) note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Note") },
            placeholder = { Text("e.g. Dinner at Toit") },
            minLines = 1,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = occurredAt.toLocalDate(),
            latest = LocalDate.now(),
            onPicked = { date ->
                pickingDate = false
                occurredAt = LocalDateTime.of(date, occurredAt.toLocalTime())
            },
            onDismiss = { pickingDate = false },
        )
    }
}
