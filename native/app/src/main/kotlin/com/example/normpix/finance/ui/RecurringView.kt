package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.MaterialTheme
import com.example.normpix.finance.domain.RecurringRule
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.upcomingOccurrences
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime

/**
 * Plan > Recurring: rent, subscriptions, salary and other regular
 * transactions, recorded automatically on this device when due.
 */
@Composable
fun RecurringView() {
    val actions = rememberFinanceActions()
    val rules by rememberFinanceQuery(Unit) { recurringRules() }
    Column(Modifier.padding(AppSpacing.page)) {
        when (val value = rules) {
            is AsyncValue.Loading -> AppLoadingState(label = "Loading recurring payments")
            is AsyncValue.Failure -> AppErrorState(title = "Couldn't load this", message = "Your data is safe on this device. Try again.")
            is AsyncValue.Data -> {
                val all = value.value
                if (all.isEmpty()) {
                    AppEmptyState(
                        icon = Icons.Outlined.EventRepeat,
                        title = "No recurring payments",
                        message = "Add rent, subscriptions, salary and other regular transactions. They're recorded on their date, on this device.",
                        actionLabel = "Add recurring payment",
                        actionIcon = Icons.Rounded.Add,
                        onAction = actions::addRecurring,
                    )
                } else {
                    val active = all.filter { it.active && !it.isFinished }
                    val paused = all.filter { !it.active && !it.isFinished }
                    val ended = all.filter { it.isFinished }
                    if (active.isNotEmpty()) {
                        NextThirtyDays(active)
                        ListSection(title = "Active") { for (r in active) item { RuleTile(r) } }
                    }
                    if (paused.isNotEmpty()) ListSection(title = "Paused") { for (r in paused) item { RuleTile(r) } }
                    if (ended.isNotEmpty()) ListSection(title = "Ended") { for (r in ended) item { RuleTile(r) } }
                    // Always at hand, not only while the list is empty.
                    ListSection(modifier = Modifier.padding(top = AppSpacing.lg)) {
                        item {
                            ListRow(
                                headline = "Add recurring payment",
                                leading = { LeadingIcon(Icons.Rounded.Add) },
                                onClick = actions::addRecurring,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Exact totals of the occurrences due in the next 30 days. */
@Composable
private fun NextThirtyDays(rules: List<RecurringRule>) {
    val finance = rememberFinanceState()
    val horizon = LocalDateTime.now().plusDays(30)
    var expense = 0L
    var income = 0L
    var count = 0
    for (rule in rules) {
        // A daily rule has at most 31 occurrences in the window.
        for (date in upcomingOccurrences(rule, 32)) {
            if (date.isAfter(horizon)) break
            count++
            when (rule.type) {
                TransactionType.Expense -> expense += rule.amount
                TransactionType.Income -> income += rule.amount
                TransactionType.Transfer -> Unit
            }
        }
    }
    ListSection(
        title = "Next 30 days",
        footer = if (count == 0) {
            "Nothing is due in the next 30 days."
        } else {
            "$count ${if (count == 1) "payment" else "payments"} due. Transfers aren't counted as spending or income."
        },
    ) {
        item {
            Row(Modifier.padding(AppSpacing.lg)) {
                SummaryStat(label = "Going out", value = finance.money.format(expense), modifier = Modifier.weight(1f))
                Spacer(Modifier.width(AppSpacing.lg))
                SummaryStat(label = "Coming in", value = finance.money.format(income), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RuleTile(rule: RecurringRule) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val isTransfer = rule.type == TransactionType.Transfer
    val category = finance.category(rule.categoryId)
    val account = finance.account(rule.accountId)
    val to = finance.account(rule.toAccountId)
    val title = rule.payee ?: if (isTransfer) "Transfer" else category?.name ?: "Uncategorized"
    val schedule = scheduleLabel(rule.frequency, rule.interval)
    val status = when {
        rule.isFinished -> "Ended"
        !rule.active -> "Paused"
        else -> "Next ${shortDate(rule.nextDate)}"
    }
    val where = if (isTransfer) "${account?.name ?: "Unknown"} → ${to?.name ?: "Unknown"}" else account?.name ?: "Unknown account"
    val subtitle = "$schedule · $status · $where"
    val amount = finance.money.format(rule.amount)

    ListRow(
        headline = title,
        supporting = subtitle,
        leading = { if (isTransfer) TransferAvatar() else CategoryAvatar(category, payee = rule.payee) },
        trailing = { TransactionAmount(rule.type, rule.amount, finance.money, style = MaterialTheme.typography.titleSmall) },
        onClick = { rule.id?.let(actions::editRecurring) },
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "${rule.type.label}, $amount, $title, $subtitle"
            onClick { rule.id?.let(actions::editRecurring); true }
        },
    )
}
