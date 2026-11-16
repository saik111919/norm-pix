package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.LoanEmi
import com.example.normpix.finance.domain.LoanOutlook
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.emiInterestCategory
import com.example.normpix.finance.domain.firstAutoEmiIndex
import com.example.normpix.finance.domain.interestOn
import com.example.normpix.finance.domain.loanOutlook
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.nextEmiIndex
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.domain.rateText
import com.example.normpix.finance.domain.settledCutoff
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * A loan's page and the sheet that records an EMI (2026-11-15). On the account's own page a loan has, above its history: how much is
 * still to pay and how many months are left, then its EMI, interest, the interest still to come and the next EMI split into principal
 * and interest, a button to record an EMI and the months still to pay. The figures come from `finance/domain/Loans.kt` and the
 * account's balance as the ledger has it, as of today - nothing about a loan is stored beyond its terms.
 */

private val MONTH_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

/** "Mar 2041": the month a loan is paid off in. */
internal fun monthYear(day: LocalDate): String = MONTH_YEAR.format(day)

/** How many months of the schedule show before "Show all". */
internal const val SCHEDULE_PREVIEW = 6

/** What is still owed on a loan account: its balance below zero, turned round - never negative (an overpaid loan owes nothing). */
internal fun outstandingOf(balance: Long): Long = (-balance).coerceAtLeast(0L)

/**
 * A loan's summary and the rest of its page above its history: the two-panel banner ("Still to pay" and "Months left"), the rows
 * (EMI, interest a year, the interest still to pay, the next EMI), Record EMI and the schedule. Nothing for an account that has no
 * loan details (which cannot be saved as a loan). While the last payment is being read it says nothing, so no figure shows twice.
 */
@Composable
fun LoanPanel(account: Account, modifier: Modifier = Modifier) {
    val terms = account.loan ?: return
    val id = account.id ?: return
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val asOf = finance.asOf ?: settledCutoff(LocalDateTime.now())
    val read by rememberFinanceQuery(id to asOf) { lastLoanPayment(id, asOf) to emiRuleOf(id) }
    val (last, emiRule) = (read as? AsyncValue.Data ?: return).value
    val money = finance.money
    val outstanding = outstandingOf(finance.balanceOf(id))
    val today = LocalDate.now()
    val nextIndex = nextEmiIndex(terms, last?.occurredAt?.toLocalDate(), today)
    val outlook = loanOutlook(terms, outstanding, nextIndex)
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxWidth().padding(bottom = AppSpacing.md)) {
        val monthsLeft = when {
            outlook == null -> "—"
            else -> outlook.monthsLeft.toString()
        }
        val payoffNote = when {
            outlook == null -> "EMI is below the interest"
            outlook.paidOff -> "Paid off"
            else -> "Paid off ${monthYear(outlook.payoff!!)}"
        }
        SplitStatBanner(
            first = BannerFigure(
                title = "Still to pay",
                figure = money.format(outstanding),
                note = "of ${money.format(terms.principal, trimZeroFraction = true)} borrowed",
            ),
            second = BannerFigure(title = "Months left", figure = monthsLeft, note = payoffNote),
            spoken = "${account.name}: ${money.format(outstanding)} still to pay" +
                if (outlook == null) ". The EMI is below the interest, so it would never be paid off." else ", $monthsLeft months left, $payoffNote.",
        )
        Spacer(Modifier.height(AppSpacing.sm))
        ListSection {
            item { FactRow("EMI", "${money.format(terms.emi)} a month") }
            item { FactRow("Interest a year", "${rateText(terms.rateBp)}%") }
            if (outlook != null && !outlook.paidOff) item { FactRow("Interest still to pay", money.format(outlook.interestLeft)) }
            outlook?.next?.let { next ->
                item {
                    FactRow(
                        label = "Next EMI",
                        value = money.format(next.payment),
                        supporting = (if (LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE) {
                            "${money.format(next.principal)} principal\n${money.format(next.interest)} interest\n"
                        } else {
                            "${money.format(next.principal)} principal + ${money.format(next.interest)} interest, "
                        }) + (if (next.date.isAfter(today)) "on " else "due ") + shortDate(next.date.atStartOfDay()),
                    )
                }
            }
        }
        if (outstanding > 0) {
            Spacer(Modifier.height(AppSpacing.md))
            // EMIs recorded each month (owner, 2026-11-16): a switch, as the Forecast's everyday spending is. On, it says from where
            // and when the next one is; turning it on asks which account pays.
            ListSection {
                item {
                    SwitchRow(
                        headline = "Record EMIs automatically",
                        supporting = if (emiRule != null) {
                            val from = finance.account(emiRule.accountId)?.name ?: "another account"
                            "${money.format(emiRule.amount)} from $from each month, next on ${shortDate(emiRule.nextDate)}. " +
                                "Each is split into principal and interest."
                        } else {
                            "Each EMI is recorded on its day, split into principal and interest, so you don't have to."
                        },
                        checked = emiRule != null,
                        onCheckedChange = { on -> if (on) actions.startLoanEmis(account) else actions.stopLoanEmis(account) },
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.md))
            FilledTonalButton(
                onClick = { actions.recordEmi(account) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(AppIconSizes.sm))
                Spacer(Modifier.width(AppSpacing.sm))
                Text("Record EMI")
            }
            Text(
                if (emiRule != null) {
                    "Record EMI is for one paid another way, or a payment on top. An EMI you record here isn't recorded again."
                } else {
                    "Record each EMI here when it's paid, or turn on Record EMIs automatically."
                },
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.sm, start = AppSpacing.lg),
            )
        }
        if (outlook != null && !outlook.paidOff) Schedule(outlook, money)
    }
}

/** One fact of the loan: its [label], the [value] at the end, and a line under the label when there is one to say. */
@Composable
private fun FactRow(label: String, value: String, supporting: String? = null) {
    ListRow(
        headline = label,
        supporting = supporting,
        supportingMaxLines = 6,
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = listOfNotNull("$label $value", supporting).joinToString(", ")
        },
        trailing = {
            Text(value, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES), maxLines = 1)
        },
    )
}

/**
 * The months still to pay: a few at first, all of them on "Show all" (the button is on the heading, so it is in reach however long the
 * list is). Each says its day, how it splits and what is owed after it.
 */
@Composable
private fun Schedule(outlook: LoanOutlook, money: MoneyFormatter) {
    var all by remember { mutableStateOf(false) }
    val rows = if (all) outlook.schedule else outlook.schedule.take(SCHEDULE_PREVIEW)
    // At large text the amount left goes under the split instead of beside it, where it left the words no room (as Repeating costs does).
    val roomy = LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE
    SectionHeader(
        title = "Schedule",
        modifier = Modifier.padding(top = AppSpacing.sm),
        actionLabel = when {
            outlook.schedule.size <= SCHEDULE_PREVIEW -> null
            all -> "Show fewer"
            else -> "Show all"
        },
        onAction = { all = !all },
    )
    ListSection(footer = "As it stands, paying the EMI every month.") {
        for (row in rows) {
            item {
                val split = "${money.format(row.principal)} principal\n${money.format(row.interest)} interest"
                ListRow(
                    headline = shortDate(row.date.atStartOfDay()),
                    supporting = if (roomy) "$split\n${money.format(row.outstandingAfter)} left" else split,
                    supportingMaxLines = 3,
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        contentDescription = "${shortDate(row.date.atStartOfDay())}: ${money.format(row.principal)} principal, " +
                            "${money.format(row.interest)} interest, ${money.format(row.outstandingAfter)} left after"
                    },
                    trailing = if (roomy) {
                        null
                    } else {
                        {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    money.format(row.outstandingAfter),
                                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                                    maxLines = 1,
                                )
                                Text("left", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------- EMIs recorded each month

/**
 * Turns on recording [loan]'s EMIs each month, as a bottom sheet: which account pays them - the one the last EMI came from
 * ([lastPayment]), else the default account - and, under it, when the first is recorded and what happens to each. Turning it on makes
 * the repeating payment ([FinanceController.startLoanEmis]). Calls [onDone] with true once on, or null when closed without.
 */
@Composable
fun AutoEmiSheet(loan: Account, lastPayment: FinanceTransaction?, onDone: (Boolean?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val money = finance.money
    val loanId = loan.id!!
    val terms = loan.loan
    val sources = finance.activeAccounts.filter { it.id != loanId && !it.isLoan }
    var sourceId by remember {
        mutableStateOf(
            lastPayment?.accountId?.takeIf { id -> sources.any { it.id == id } }
                ?: finance.defaultAccount?.id?.takeIf { id -> sources.any { it.id == id } }
                ?: sources.firstOrNull()?.id,
        )
    }
    val firstDay = remember(loanId) { terms?.dateOf(firstAutoEmiIndex(terms, lastPayment?.occurredAt?.toLocalDate(), LocalDate.now())) }
    var saving by remember { mutableStateOf(false) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun save() {
        formError = null
        val from = sourceId
        if (from == null) {
            sourceError = "Choose where the money comes from"
            return
        }
        saving = true
        scope.launch {
            try {
                controller.startLoanEmis(loanId, from)
                haptics.success()
                onDone(true)
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't turn it on. Nothing was changed."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = "Record EMIs automatically",
        subtitle = listOfNotNull(loan.name, terms?.let { "EMI ${money.format(it.emi)}" }).joinToString(" · "),
        saveLabel = "Turn on",
        onSave = ::save,
        saving = saving,
        errorText = formError,
    ) {
        Text(
            "Each month on its day the EMI is recorded from the account below, split as Record EMI splits it: the principal lowers " +
                "what you owe, the interest is spending.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        if (sources.isEmpty()) {
            Text(
                "Add another account in Wallet to pay from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            AccountSelector(
                label = "Paid from",
                accounts = sources,
                selected = sourceId,
                onSelect = {
                    sourceId = it
                    sourceError = null
                },
                errorText = sourceError,
            )
        }
        if (firstDay != null) {
            Spacer(Modifier.height(AppSpacing.lg))
            Text(
                "The first is recorded on ${shortDate(firstDay.atStartOfDay())}. An EMI you record yourself isn't recorded again, " +
                    "and it stops once the loan is paid off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------------------- record an EMI

/**
 * Records one EMI of [loan], as a bottom sheet: from which account (the one the last EMI came from, else the default account), on
 * which day (today), and how it splits - the principal and the interest, both worked out from what is still owed and both yours to
 * change. Saving makes **two entries in one transaction**: a transfer of the principal onto the loan, which lowers what is owed and is
 * not spending, and an expense of the interest in the Loans & EMI category, which is. Either both are saved or neither.
 * [lastPayment] is the latest payment onto the loan (where the account to pay from starts), if there has been one.
 * Calls [onDone] with true once saved, or null when closed without recording.
 */
@Composable
fun RecordEmiSheet(loan: Account, lastPayment: FinanceTransaction?, onDone: (Boolean?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val money = finance.money
    val decimals = finance.currency.decimals
    val terms = loan.loan
    val loanId = loan.id!!

    val sources = finance.activeAccounts.filter { it.id != loanId && !it.isLoan }
    val outstanding = outstandingOf(finance.balanceOf(loanId))
    // What the next EMI splits into: the schedule's next month, or - where the EMI can never pay it off - the EMI split by one month's interest.
    val suggestion = remember(loanId) {
        if (terms == null || outstanding <= 0) {
            0L to 0L
        } else {
            val next = loanOutlook(terms, outstanding, nextEmiIndex(terms, lastPayment?.occurredAt?.toLocalDate(), LocalDate.now()))?.next
            if (next != null) {
                next.principal to next.interest
            } else {
                val interest = interestOn(outstanding, terms.rateBp)
                (terms.emi - interest).coerceIn(0L, outstanding) to interest.coerceAtMost(terms.emi)
            }
        }
    }
    var principalText by remember { mutableStateOf(if (suggestion.first > 0) minorToInputText(suggestion.first, decimals) else "") }
    var interestText by remember { mutableStateOf(if (suggestion.second > 0) minorToInputText(suggestion.second, decimals) else "") }
    var sourceId by remember {
        mutableStateOf(
            (lastPayment?.accountId?.takeIf { id -> sources.any { it.id == id } }
                ?: finance.defaultAccount?.id?.takeIf { id -> sources.any { it.id == id } }
                ?: sources.firstOrNull()?.id),
        )
    }
    var categoryId by remember { mutableStateOf(emiInterestCategory(finance.categories)?.id) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickingDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var principalError by remember { mutableStateOf<String?>(null) }
    var interestError by remember { mutableStateOf<String?>(null) }
    var sourceError by remember { mutableStateOf<String?>(null) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }

    val initialSnapshot = remember { listOf(principalText, interestText, sourceId, categoryId, date).joinToString("|") }
    DiscardChangesGuard(
        dirty = listOf(principalText, interestText, sourceId, categoryId, date).joinToString("|") != initialSnapshot,
        saving = saving,
        isEditing = false,
        keptMessage = "",
    )

    fun part(text: String): Long? = if (text.isBlank()) 0L else parseMinorUnits(text, decimals)
    val principal = part(principalText)
    val interest = part(interestText)
    val total = (principal ?: 0L) + (interest ?: 0L)
    val categories = finance.categoriesOf(CategoryType.Expense)

    fun save() {
        formError = null
        principalError = if (principal == null) "Enter a valid amount" else null
        interestError = if (interest == null) "Enter a valid amount" else null
        sourceError = if (sourceId == null) "Choose where the money comes from" else null
        categoryError = if ((interest ?: 0L) > 0 && categoryId == null) "Choose a category for the interest" else null
        if (principal == null || interest == null) return
        if (principal == 0L && interest == 0L) {
            formError = "Enter the principal, the interest, or both."
            return
        }
        if (sourceError != null || categoryError != null) return
        saving = true
        val emi = LoanEmi(
            loanId = loanId,
            fromAccountId = sourceId!!,
            at = LocalDateTime.of(date, LocalTime.now().withSecond(0).withNano(0)),
            principal = principal,
            interest = interest,
            interestCategoryId = categoryId,
            payee = loan.name,
        )
        scope.launch {
            try {
                controller.recordLoanEmi(emi)
                haptics.success()
                snackbars.show("EMI recorded on ${loan.name}")
                onDone(true)
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't save. Nothing was recorded."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = "Record EMI",
        subtitle = listOfNotNull(loan.name, terms?.let { "EMI ${money.format(it.emi)}" }, "${money.format(outstanding)} to pay").joinToString(" · "),
        saveLabel = if (total > 0) "Record ${money.format(total)}" else "Record EMI",
        onSave = ::save,
        saving = saving,
        errorText = formError,
    ) {
        Text(
            "The principal lowers what you owe; the interest is spending.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = principalText,
            onValueChange = { entered ->
                principalText = entered.filter { it.isDigit() || it == '.' }
                principalError = null
                formError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Principal") },
            prefix = { Text(finance.currency.symbol) },
            isError = principalError != null,
            supportingText = { Text(principalError ?: "The part that lowers what you owe") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
            shape = TonalFieldShape,
            colors = typedFieldColors(),
        )
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = interestText,
            onValueChange = { entered ->
                interestText = entered.filter { it.isDigit() || it == '.' }
                interestError = null
                formError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Interest") },
            prefix = { Text(finance.currency.symbol) },
            isError = interestError != null,
            supportingText = { Text(interestError ?: "The part that is spending") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            shape = TonalFieldShape,
            colors = typedFieldColors(),
        )
        if (terms != null && principal != null && interest != null && total > 0) {
            Spacer(Modifier.height(AppSpacing.sm))
            val gap = total - terms.emi
            Text(
                when {
                    gap == 0L -> "That is the EMI, ${money.format(terms.emi)}."
                    gap > 0 -> "That is ${money.format(gap)} more than the EMI of ${money.format(terms.emi)}."
                    else -> "That is ${money.format(-gap)} less than the EMI of ${money.format(terms.emi)}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg),
            )
        }
        if ((interest ?: 0L) > 0) {
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
        Spacer(Modifier.height(AppSpacing.lg))
        if (sources.isEmpty()) {
            Text(
                "Add another account in Wallet to pay from.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        } else {
            AccountSelector(
                label = "Paid from",
                accounts = sources,
                selected = sourceId,
                onSelect = {
                    sourceId = it
                    sourceError = null
                },
                errorText = sourceError,
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        PickerFieldRow(PickerField("Date", shortDate(date.atStartOfDay())) { pickingDate = true })
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = date,
            onPicked = {
                pickingDate = false
                date = it
            },
            onDismiss = { pickingDate = false },
        )
    }
}
