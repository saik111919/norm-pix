package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.LoanTerms
import com.example.normpix.finance.domain.MAX_LOAN_MONTHS
import com.example.normpix.finance.domain.MAX_LOAN_RATE_BP
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.PayoffMonths
import com.example.normpix.finance.domain.emiFor
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.monthsToPay
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.domain.parseRateBp
import com.example.normpix.finance.domain.rateText
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate

/*
 * The "Loan details" of the account form (2026-11-15): what was borrowed, the interest a year, the EMI or the months, and the day of
 * the first EMI. Shown when the account is of the Loan kind or has "This is a loan" on. Typed as the other fields are (typed outline,
 * errors under a field after Save), and worked out with the pure maths in `finance/domain/Loans.kt`: give the EMI and the months it
 * takes are worked out, give the months and the EMI is.
 */

/** What the loan fields say once typed: the terms when they all hold, else what is wrong with each. */
class LoanFormResult(
    val terms: LoanTerms?,
    val borrowedProblem: String?,
    val rateProblem: String?,
    val termProblem: String?,
)

/** The loan fields' text and choices, kept in the form. [existing] is the loan being edited, if any. */
@Stable
class LoanFormState(existing: LoanTerms?, decimals: Int) {
    var borrowed by mutableStateOf(existing?.let { minorToInputText(it.principal, decimals) }.orEmpty())
    var rate by mutableStateOf(existing?.let { rateText(it.rateBp) }.orEmpty())

    /** Whether the EMI is typed (and the months worked out) rather than the months (and the EMI worked out). */
    var byEmi by mutableStateOf(true)
    var emi by mutableStateOf(existing?.let { minorToInputText(it.emi, decimals) }.orEmpty())
    var months by mutableStateOf(existing?.tenureMonths?.toString().orEmpty())

    /** The day of the first EMI; a new loan starts at a month from today, when a loan made today first asks for one. */
    var firstEmi by mutableStateOf(existing?.start ?: LocalDate.now().plusMonths(1))
    var pickingDate by mutableStateOf(false)

    /** What the last Save said no to, under each field; cleared as the field is changed. */
    var borrowedError by mutableStateOf<String?>(null)
    var rateError by mutableStateOf<String?>(null)
    var termError by mutableStateOf<String?>(null)

    /** Everything that can change, as one text: the form asks before closing over changes. */
    fun snapshot(): String = listOf(borrowed, rate, byEmi, emi, months, firstEmi).joinToString("|")

    /** Reads what is typed: the [LoanTerms] when all holds, else the problem with each field, in plain words. */
    fun resolve(decimals: Int, money: MoneyFormatter): LoanFormResult {
        val principal = parseMinorUnits(borrowed, decimals)
        val borrowedProblem = when {
            borrowed.isBlank() -> "Enter the amount you borrowed"
            principal == null -> "Enter a valid amount"
            principal <= 0L -> "Enter an amount above zero"
            else -> null
        }
        val bp = parseRateBp(rate)
        val rateProblem = when {
            rate.isBlank() -> "Enter the interest a year (0 if there is none)"
            bp == null -> "Enter a rate such as 9 or 10.5"
            bp !in 0..MAX_LOAN_RATE_BP -> "Enter a rate from 0 to 100"
            else -> null
        }
        val known = borrowedProblem == null && rateProblem == null
        var emiMinor: Long? = null
        var tenure: Int? = null
        var termProblem: String? = null
        if (byEmi) {
            val typed = parseMinorUnits(emi, decimals)
            termProblem = when {
                emi.isBlank() -> "Enter the EMI"
                typed == null -> "Enter a valid amount"
                typed <= 0L -> "Enter an EMI above zero"
                else -> null
            }
            if (termProblem == null && known) {
                when (val payoff = monthsToPay(principal!!, bp!!, typed!!)) {
                    is PayoffMonths.Months -> {
                        emiMinor = typed
                        tenure = payoff.count
                    }
                    is PayoffMonths.BelowInterest ->
                        termProblem = "That EMI is less than the first month's interest (${money.format(payoff.interest)}). Enter a bigger EMI."
                    PayoffMonths.TooLong ->
                        termProblem = "That EMI would take more than ${MAX_LOAN_MONTHS / 12} years to pay off. Enter a bigger EMI."
                }
            }
        } else {
            val typed = months.trim().toIntOrNull()
            termProblem = when {
                months.isBlank() -> "Enter the number of months"
                typed == null || typed !in 1..MAX_LOAN_MONTHS -> "Enter a number of months from 1 to $MAX_LOAN_MONTHS"
                else -> null
            }
            if (termProblem == null && known) {
                tenure = typed
                emiMinor = emiFor(principal!!, bp!!, typed!!)
            }
        }
        val terms = if (known && termProblem == null && emiMinor != null && tenure != null) {
            LoanTerms(principal!!, bp!!, tenure, emiMinor, firstEmi)
        } else {
            null
        }
        return LoanFormResult(terms, borrowedProblem, rateProblem, termProblem)
    }
}

/**
 * The loan fields. [onBorrowedChange] tells the form what was typed as the amount borrowed, which a new loan's starting balance
 * follows until it is changed by hand. A line under the EMI or months says what the other works out to, as soon as it can.
 */
@Composable
fun LoanDetailsFields(
    state: LoanFormState,
    currency: CurrencySpec,
    money: MoneyFormatter,
    modifier: Modifier = Modifier,
    onBorrowedChange: (String) -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val result = state.resolve(currency.decimals, money)
    Column(modifier.fillMaxWidth()) {
        Text(
            "Loan details",
            style = MaterialTheme.typography.titleSmall,
            color = scheme.primary,
            modifier = Modifier.padding(start = AppSpacing.xs, bottom = AppSpacing.sm).semantics { heading() },
        )
        OutlinedTextField(
            value = state.borrowed,
            onValueChange = { entered ->
                state.borrowed = entered.filter { it.isDigit() || it == '.' }
                state.borrowedError = null
                onBorrowedChange(state.borrowed)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount borrowed") },
            prefix = { Text(currency.symbol) },
            isError = state.borrowedError != null,
            supportingText = state.borrowedError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
            shape = TonalFieldShape,
            colors = typedFieldColors(),
        )
        Spacer(Modifier.height(AppSpacing.lg))
        OutlinedTextField(
            value = state.rate,
            onValueChange = { entered ->
                state.rate = entered.filter { it.isDigit() || it == '.' }
                state.rateError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Interest a year") },
            suffix = { Text("%") },
            isError = state.rateError != null,
            supportingText = state.rateError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
            shape = TonalFieldShape,
            colors = typedFieldColors(),
        )
        Spacer(Modifier.height(AppSpacing.lg))
        ConnectedChoiceRow(
            options = remember {
                listOf(
                    ChoiceOption(true, "EMI", Icons.Outlined.Payments),
                    ChoiceOption(false, "Months", Icons.Outlined.Schedule),
                )
            },
            selected = state.byEmi,
            onSelect = {
                state.byEmi = it
                state.termError = null
            },
        )
        Spacer(Modifier.height(AppSpacing.md))
        if (state.byEmi) {
            OutlinedTextField(
                value = state.emi,
                onValueChange = { entered ->
                    state.emi = entered.filter { it.isDigit() || it == '.' }
                    state.termError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("EMI a month") },
                prefix = { Text(currency.symbol) },
                isError = state.termError != null,
                supportingText = {
                    Text(state.termError ?: result.terms?.let { "Paid off in ${it.tenureMonths} months" } ?: "What you pay every month")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                singleLine = true,
                shape = TonalFieldShape,
                colors = typedFieldColors(),
            )
        } else {
            OutlinedTextField(
                value = state.months,
                onValueChange = { entered ->
                    state.months = entered.filter { it.isDigit() }
                    state.termError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Months to pay") },
                isError = state.termError != null,
                supportingText = {
                    Text(state.termError ?: result.terms?.let { "EMI ${money.format(it.emi)} a month" } ?: "How many monthly payments it takes")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                singleLine = true,
                shape = TonalFieldShape,
                colors = typedFieldColors(),
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))
        PickerFieldRow(PickerField("First EMI date", shortDate(state.firstEmi.atStartOfDay())) { state.pickingDate = true })
        Text(
            "Each EMI falls on this day of the month. NormPix doesn't record them by itself: you record each one when it's paid.",
            modifier = Modifier.padding(top = AppSpacing.sm, start = AppSpacing.lg),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (state.pickingDate) {
        DatePickerDialogFor(
            initial = state.firstEmi,
            onPicked = {
                state.pickingDate = false
                state.firstEmi = it
            },
            onDismiss = { state.pickingDate = false },
        )
    }
}
