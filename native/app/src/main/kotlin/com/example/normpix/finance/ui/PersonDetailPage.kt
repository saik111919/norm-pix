package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonOff
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.PeriodSummary
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.percentLabel
import com.example.normpix.finance.domain.shareOf
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing

/**
 * One person's page: what has been spent on them and every expense that names them, newest first. It is the transaction history
 * narrowed to that person, so it has the same month / all-time switch, search, date range and filters - and its header adds up
 * whatever the list is showing. Edit, archive and delete are behind the "more" button; "+" adds an expense already for them.
 */
@Composable
fun PersonDetailPage(personId: Long) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val person = finance.person(personId)
    if (person == null) {
        // Deleted while open (or an old link): nothing to show but the way back.
        AppSubPage(title = "Person", onBack = navigator::back) {
            item {
                AppEmptyState(
                    icon = Icons.Outlined.PersonOff,
                    title = "This person is gone",
                    message = "Their expenses are still in your history; they just aren't linked to anyone.",
                    actionLabel = "Back to People",
                    onAction = navigator::back,
                )
            }
        }
        return
    }
    TransactionHistory(
        title = person.name,
        onBack = navigator::back,
        lockedPersonId = personId,
        actions = {
            if (!person.archived) {
                IconButton(onClick = { actions.addTransaction(TransactionType.Expense, personId = personId) }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add expense for ${person.name}")
                }
            }
            IconButton(onClick = { actions.managePerson(person, onDeleted = navigator::back, showOpen = false) }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Person options")
            }
        },
        summaryHeader = { view -> PersonSummary(person, view) },
        showSpentLine = false,
    )
}

/**
 * The person at the top of their page: who they are, what was spent on them in the period the list below shows (it follows the
 * month, search and filters), how many expenses that was and the average, and their share of everything spent in that period.
 */
@Composable
private fun PersonSummary(person: Person, view: HistoryView) {
    val finance = rememberFinanceState()
    val money = finance.money
    // Everything spent in the same stretch of time, whoever it was for: what the share is a share of.
    val everything by rememberFinanceQuery(view.query.range) { summaryFor(TransactionQuery(range = view.query.range)) }
    val whole = (everything as? AsyncValue.Data)?.value ?: PeriodSummary.Empty
    val spent = view.summary.expense
    val count = view.summary.count
    val share = shareOf(spent, whole.expense)
    val narrowed = view.query.hasFiltersBeyondPerson

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = AppRadius.rXl,
        modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.md),
    ) {
        Column(Modifier.padding(AppSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PersonAvatar(person, size = 48.dp)
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        person.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        if (person.archived) "Archived · ${view.periodLabel}" else view.periodLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.lg))
            SummaryStat(
                label = if (narrowed) "Spent · filtered" else "Spent",
                value = money.format(spent),
                valueStyle = MaterialTheme.typography.displaySmall,
            )
            if (whole.expense > 0 && !narrowed) {
                Spacer(Modifier.height(AppSpacing.md))
                AppLinearProgress(Modifier.fillMaxWidth(), progress = { share.toFloat() })
            }
            Spacer(Modifier.height(AppSpacing.md))
            HeroStats(
                buildList<@Composable (Modifier) -> Unit> {
                    add { m -> SummaryStat(label = "Expenses", value = count.toString(), modifier = m) }
                    if (count > 0) add { m -> SummaryStat(label = "Average", value = money.format((spent + count / 2) / count), modifier = m) }
                    if (whole.expense > 0 && !narrowed) {
                        add { m -> SummaryStat(label = "Of all spending", value = percentOf(share), modifier = m) }
                    }
                },
            )
        }
    }
}

/** "34%", "<1%" for a sliver, "0%" for nothing: the same as every other screen (see [percentLabel]). */
private fun percentOf(share: Double): String = percentLabel(share)

/** The query narrowed by anything the person's own page doesn't already imply (search, categories, accounts, amounts, a type). */
private val TransactionQuery.hasFiltersBeyondPerson: Boolean
    get() = types.isNotEmpty() || accountIds.isNotEmpty() || categoryIds.isNotEmpty() || minAmount != null || maxAmount != null ||
        !text.isNullOrBlank()
