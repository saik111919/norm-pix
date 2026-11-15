package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.RecurrenceFrequency
import com.example.normpix.finance.domain.RepeatSuggestion
import com.example.normpix.finance.domain.RepeatingCost
import com.example.normpix.finance.domain.RepeatingCosts
import com.example.normpix.finance.domain.repeatingCosts
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.ListSegment
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing

/*
 * What the payments that repeat cost, and the ones that look like they should (2026-11-14): on Plans > Recurring, a banner with a
 * month's and a year's cost that opens the page of each one's cost (with a pause on each), and cards offering the payees that look
 * like a repeating payment. The sums are in finance/domain (`repeatingCosts`, `suggestRepeats`); this only says them.
 */

private fun payments(count: Int) = if (count == 1) "payment" else "payments"

/** "every week", "every 2 months": how often, as a sentence goes on from an amount ("₹649.00 every month"). */
internal fun everyWords(frequency: RecurrenceFrequency, interval: Int = 1): String =
    "every " + if (interval <= 1) frequency.unit(1) else "$interval ${frequency.unit(interval)}"

/**
 * The two-panel banner on Plans > Recurring: what the repeating payments cost "A month" and "A year", added up from each one's
 * own cost ([repeatingCosts]). Pressing it opens the page that lists them ([onOpen], none on that page itself).
 */
@Composable
internal fun RepeatingCostsBanner(costs: RepeatingCosts, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    val money = rememberFinanceState().money
    val count = costs.active.size
    val month = money.format(costs.perMonth)
    val year = money.format(costs.perYear)
    SplitStatBanner(
        first = BannerFigure(title = "A month", figure = month, note = "$count repeating ${payments(count)}"),
        second = BannerFigure(title = "A year", figure = year, note = "Transfers aren't counted"),
        spoken = "Repeating payments cost $month a month and $year a year, across $count ${payments(count)}.",
        modifier = modifier,
        onClickLabel = "See what each one costs",
        onClick = onOpen,
    )
}

// ------------------------------------------------------------------------------------------------------- suggestions

/**
 * The payees that look like a repeating payment, as cards under the banner: what was seen, and two ways to answer - **Add** opens the
 * repeating-payment form filled in, **Not this one** is remembered for good. Nothing at all while there are none.
 */
@Composable
internal fun RepeatSuggestions(suggestions: List<RepeatSuggestion>, modifier: Modifier = Modifier) {
    if (suggestions.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        SectionSubheader(if (suggestions.size == 1) "Looks like a repeating payment" else "These look like repeating payments")
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            for (suggestion in suggestions) RepeatSuggestionCard(suggestion)
        }
    }
}

@Composable
private fun RepeatSuggestionCard(suggestion: RepeatSuggestion) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val scheme = MaterialTheme.colorScheme
    val what = "${finance.money.format(suggestion.amount)} ${everyWords(suggestion.frequency)}"
    val seen = "seen ${suggestion.seen} times"
    val next = "Next one expected ${shortDate(suggestion.nextDate.atStartOfDay())}"
    ListSegment {
        Column(Modifier.padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.sm, bottom = AppSpacing.sm)) {
            Row(
                Modifier
                    .padding(end = AppSpacing.md)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Looks like a repeating payment: ${suggestion.payee}, $what, $seen. $next."
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryAvatar(finance.category(suggestion.categoryId), payee = suggestion.payee)
                Spacer(Modifier.width(AppSpacing.md))
                Column(Modifier.weight(1f)) {
                    Text(suggestion.payee, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("$what · $seen", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    Text(next, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { actions.dismissRepeat(suggestion) },
                    modifier = Modifier.semantics { contentDescription = "Not this one: don't suggest ${suggestion.payee} again" },
                ) { Text("Not this one") }
                Spacer(Modifier.width(AppSpacing.xs))
                FilledTonalButton(
                    onClick = { actions.addSuggestedRecurring(suggestion) },
                    modifier = Modifier.semantics { contentDescription = "Add ${suggestion.payee} as a repeating payment" },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(AppIconSizes.sm))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("Add")
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------------------- the page

/**
 * Repeating costs: every repeating expense, the dearest first, with its amount and how often, what it comes to a month, when it is next
 * charged and a switch to pause (or resume) it. The banner on top is the one on Plans > Recurring. A paused payment is listed after the
 * others, costs nothing while it is, and is resumed from here.
 */
@Composable
fun RepeatingCostsPage() {
    val navigator = LocalNavigator.current
    val actions = rememberFinanceActions()
    val rules by rememberFinanceQuery(Unit) { recurringRules() }
    AppSubPage(title = "Repeating costs", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        item {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = rules) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Loading repeating costs")
                    is AsyncValue.Failure -> AppErrorState(title = "Couldn't load this", message = "Your data is safe on this device. Try again.")
                    is AsyncValue.Data -> {
                        val costs = repeatingCosts(value.value)
                        if (costs.active.isEmpty() && costs.paused.isEmpty()) {
                            AppEmptyState(
                                icon = Icons.Outlined.EventRepeat,
                                title = "No repeating costs",
                                message = "Rent, subscriptions and bills that repeat show up here with what each one costs a month and a year.",
                                actionLabel = "Add recurring payment",
                                actionIcon = Icons.Rounded.Add,
                                onAction = actions::addRecurring,
                            )
                        } else {
                            RepeatingCostsBanner(costs)
                            Spacer(Modifier.height(AppSpacing.sm))
                            if (costs.active.isNotEmpty()) {
                                ListSection(
                                    title = "Biggest first",
                                    footer = "A month is the year's cost divided by 12. Income and transfers, such as a card bill paid from the bank, are not counted.",
                                ) {
                                    for (cost in costs.active) item { CostRow(cost) }
                                }
                            }
                            if (costs.paused.isNotEmpty()) {
                                ListSection(
                                    modifier = Modifier.padding(top = AppSpacing.lg),
                                    title = "Paused",
                                    footer = "A paused payment isn't recorded and costs nothing until you resume it. Nothing is deleted.",
                                ) {
                                    for (cost in costs.paused) item { CostRow(cost) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One repeating expense: the avatar, its name, "₹649.00 · Monthly" and when it is next charged, what it comes to a month at the end, and a
 * switch beside it that is the rule's active flag: on, it is recorded; off, it is paused (and the switch is how it is resumed). The row
 * opens the payment's own form; the switch is not inside it, so the two stay two controls to a screen reader.
 */
@Composable
private fun CostRow(cost: RepeatingCost) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val scheme = MaterialTheme.colorScheme
    val rule = cost.rule
    val paused = !rule.active
    val category = finance.category(rule.categoryId)
    val title = rule.payee ?: category?.name ?: "Uncategorized"
    val money = finance.money
    val schedule = scheduleLabel(rule.frequency, rule.interval)
    val status = if (paused) "Paused" else "Next ${shortDate(rule.nextDate)}"
    val spoken = "$title, ${money.format(rule.amount)} ${schedule.lowercase()}, ${money.format(cost.perMonth)} a month, " +
        "${money.format(cost.perYear)} a year, $status"
    Row(Modifier.fillMaxWidth().alpha(if (paused) PAUSED_ALPHA else 1f), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClickLabel = "Edit $title", role = Role.Button) { rule.id?.let(actions::editRecurring) }
                .semantics(mergeDescendants = true) { contentDescription = spoken }
                .padding(start = AppSpacing.lg, top = AppSpacing.md, bottom = AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CategoryAvatar(category, payee = rule.payee)
            Spacer(Modifier.width(AppSpacing.md))
            // At large text the month's figure goes under the rest instead of beside it, where it left the words no room to stay whole.
            val roomy = LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE
            val perMonth = money.format(cost.perMonth)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${money.format(rule.amount)} · $schedule", style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                Text(status, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                if (roomy) {
                    Text(
                        "$perMonth a month",
                        style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                        modifier = Modifier.padding(top = AppSpacing.xs),
                    )
                }
            }
            if (!roomy) {
                Spacer(Modifier.width(AppSpacing.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(perMonth, style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES), maxLines = 1)
                    Text("a month", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                }
            }
        }
        // The rule's active flag: off is paused (nothing is recorded, its reminder goes), on is resumed. The tick on the thumb says on
        // without the colour.
        Switch(
            checked = !paused,
            onCheckedChange = { on -> actions.setRecurringPaused(rule, paused = !on) },
            modifier = Modifier.padding(start = AppSpacing.sm, end = AppSpacing.lg).semantics { contentDescription = "Active: $title" },
            thumbContent = if (!paused) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(SwitchDefaults.IconSize)) }
            } else {
                null
            },
        )
    }
}

/** How faint a paused payment is drawn: still readable (it is text), plainly not live - as the Recurring page draws one. */
private const val PAUSED_ALPHA = 0.7f
