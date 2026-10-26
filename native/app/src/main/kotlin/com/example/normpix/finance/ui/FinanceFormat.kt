package com.example.normpix.finance.ui

import android.text.format.DateFormat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.example.normpix.finance.domain.AmountSign
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * Shared finance text formatting, so every screen phrases amounts, dates and
 * transaction types the same way.
 */

val TransactionType.label: String
    get() = when (this) {
        TransactionType.Expense -> "Expense"
        TransactionType.Income -> "Income"
        TransactionType.Transfer -> "Transfer"
    }

val TransactionType.icon: ImageVector
    get() = when (this) {
        TransactionType.Expense -> Icons.Rounded.ArrowUpward
        TransactionType.Income -> Icons.Rounded.ArrowDownward
        TransactionType.Transfer -> Icons.Rounded.SwapHoriz
    }

/**
 * Signed display amount: "-Rs 500.00" (expense), "+Rs 500.00" (income),
 * "Rs 500.00" (transfer - money moved, not gained or spent).
 */
fun signedAmount(
    money: MoneyFormatter,
    type: TransactionType,
    amount: Long,
    direction: TransferDirection? = null,
): String = when (type) {
    TransactionType.Expense -> money.format(-amount)
    TransactionType.Income -> money.format(amount, AmountSign.Explicit)
    TransactionType.Transfer -> when (direction) {
        TransferDirection.In -> money.format(amount, AmountSign.Explicit)
        TransferDirection.Out -> money.format(-amount)
        null -> money.format(amount)
    }
}

/** Which way a transfer runs, seen from one of its two accounts (so that account's list adds up to its balance). */
enum class TransferDirection(val label: String) {
    In("Transfer in"),
    Out("Transfer out"),
}

/** [transaction]'s direction for [accountId]; null when it is not a transfer, or not one of that account's. */
fun transferDirection(transaction: FinanceTransaction, accountId: Long?): TransferDirection? = when {
    accountId == null || transaction.type != TransactionType.Transfer -> null
    transaction.toAccountId == accountId -> TransferDirection.In
    transaction.accountId == accountId -> TransferDirection.Out
    else -> null
}

/** Spoken form of [signedAmount]: "expense of ..." so the direction never depends on a symbol. */
fun spokenAmount(money: MoneyFormatter, type: TransactionType, amount: Long): String =
    "${type.label.lowercase()} of ${money.format(amount)}"

/** Tabular figures keep columns of amounts aligned. */
const val TABULAR_FIGURES = "tnum"

/**
 * The amount of a transaction, styled by type. Color is only a secondary cue
 * - the sign and the row's icon/label always carry the meaning too.
 */
@Composable
fun TransactionAmount(
    type: TransactionType,
    amount: Long,
    money: MoneyFormatter,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    /** For a transfer listed under one of its accounts: whether the money came in or went out there. */
    direction: TransferDirection? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val color = when (type) {
        TransactionType.Income -> StatusTone.Success.resolve().color
        TransactionType.Expense -> scheme.onSurface
        TransactionType.Transfer -> scheme.onSurfaceVariant
    }
    Text(
        signedAmount(money, type, amount, direction),
        modifier = modifier,
        style = style.copy(fontFeatureSettings = TABULAR_FIGURES),
        color = color,
    )
}

private fun fmt(pattern: String) = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())

/** Group header for a day in the transaction timeline. */
fun dayHeading(day: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val today = startOfDay(now).toLocalDate()
    val target = startOfDay(day).toLocalDate()
    val diff = ChronoUnit.DAYS.between(target, today)
    return when {
        diff == 0L -> "Today"
        diff == 1L -> "Yesterday"
        diff == -1L -> "Tomorrow"
        diff in 2..6 -> fmt("EEEE").format(day)
        target.year == today.year -> fmt("EEE, d MMM").format(day)
        else -> fmt("EEE, d MMM yyyy").format(day)
    }
}

/** "12 Sep" (or "12 Sep 2025" outside the current year). */
fun shortDate(day: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String =
    if (day.year == now.year) fmt("d MMM").format(day) else fmt("d MMM yyyy").format(day)

/** "12 Sep, 6:30 PM" using the device's 12/24-hour preference. */
@Composable
fun dateTimeLabel(moment: LocalDateTime): String {
    val context = LocalContext.current
    return dateTimeLabel(moment, DateFormat.is24HourFormat(context))
}

fun dateTimeLabel(moment: LocalDateTime, is24Hour: Boolean): String =
    "${shortDate(moment)}, ${timeLabel(moment, is24Hour)}"

fun timeLabel(moment: LocalDateTime, is24Hour: Boolean): String =
    fmt(if (is24Hour) "HH:mm" else "h:mm a").format(moment)

/** "September" for the current year, "September 2025" otherwise. */
fun monthLabel(month: MonthKey, now: LocalDateTime = LocalDateTime.now()): String =
    if (month.year == now.year) fmt("MMMM").format(month.start) else fmt("MMMM yyyy").format(month.start)

fun monthLabelFull(month: MonthKey): String = fmt("MMMM yyyy").format(month.start)

fun monthShort(month: MonthKey): String = fmt("MMM").format(month.start)

val RecurrenceFrequency.label: String
    get() = when (this) {
        RecurrenceFrequency.Daily -> "Daily"
        RecurrenceFrequency.Weekly -> "Weekly"
        RecurrenceFrequency.Monthly -> "Monthly"
        RecurrenceFrequency.Yearly -> "Yearly"
    }

/** "day" / "days", etc. */
fun RecurrenceFrequency.unit(count: Int): String {
    val singular = when (this) {
        RecurrenceFrequency.Daily -> "day"
        RecurrenceFrequency.Weekly -> "week"
        RecurrenceFrequency.Monthly -> "month"
        RecurrenceFrequency.Yearly -> "year"
    }
    return if (count == 1) singular else "${singular}s"
}

/** "Monthly", or "Every 2 weeks". */
fun scheduleLabel(frequency: RecurrenceFrequency, interval: Int): String =
    if (interval <= 1) frequency.label else "Every $interval ${frequency.unit(interval)}"
