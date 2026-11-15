package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.normpix.finance.data.forecastFacts
import com.example.normpix.finance.domain.Forecast
import com.example.normpix.finance.domain.ForecastEnd
import com.example.normpix.finance.domain.ForecastFacts
import com.example.normpix.finance.domain.ForecastItem
import com.example.normpix.finance.domain.ForecastSource
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.buildForecast
import com.example.normpix.finance.domain.endDate
import com.example.normpix.finance.domain.forecastLimit
import com.example.normpix.finance.domain.nextPayday
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.LocalShellState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate

/*
 * The cash-flow forecast page (2026-11-14), opened from Plans > Recurring: the money that can be spent, day by day, from today to a day
 * the user chooses - 30, 60 or 90 days, until payday (the next of the biggest income that repeats) or a day picked from a calendar up
 * to 12 months ahead. The working-out is `buildForecast` (finance/domain); this page reads the facts once, lets the choices be changed
 * without reading them again, and says the result: the end and the lowest point in the banner, the line, and what is dated.
 */

/** One choice of where the forecast ends. */
private class EndChoice(val label: String, val end: ForecastEnd, val selected: Boolean, val spoken: String)

@Composable
fun ForecastPage() {
    val navigator = LocalNavigator.current
    val today = LocalDate.now()
    val facts by rememberFinanceQuery(today) { forecastFacts(today) }
    AppSubPage(title = "Forecast", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        item {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = facts) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Working out your forecast")
                    is AsyncValue.Failure -> AppErrorState(title = "Couldn't load this", message = "Your data is safe on this device. Try again.")
                    is AsyncValue.Data -> ForecastBody(value.value)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ForecastBody(facts: ForecastFacts) {
    val shell = LocalShellState.current
    val finance = rememberFinanceState()
    val money = finance.money
    val today = facts.today
    val payday = remember(facts) { nextPayday(facts.rules, today) }
    // "Until payday" with no income to go by is not on offer, and a session that chose it is back at 30 days.
    val chosen = shell.forecastEnd.let { if (it == ForecastEnd.Payday && payday == null) ForecastEnd.Days(30) else it }
    val end = chosen.endDate(today, payday)
    val everyday = shell.forecastEveryday
    val forecast = remember(facts, end, everyday) { buildForecast(facts, end, everyday) }
    var picking by remember { mutableStateOf(false) }

    val choices = buildList {
        for (days in listOf(30, 60, 90)) {
            add(EndChoice("$days days", ForecastEnd.Days(days), chosen == ForecastEnd.Days(days), "$days days ahead"))
        }
        if (payday != null) {
            add(EndChoice("Until payday", ForecastEnd.Payday, chosen == ForecastEnd.Payday, "Until payday, ${dayLabel(payday)}"))
        }
        val picked = chosen as? ForecastEnd.Date
        add(
            EndChoice(
                label = if (picked != null) "Until ${dayLabel(end)}" else "Pick a date",
                end = ForecastEnd.Date(end),
                selected = picked != null,
                spoken = if (picked != null) "Until ${dayLabel(end)}, change the date" else "Pick a date",
            ),
        )
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        for (choice in choices) {
            FilterChip(
                selected = choice.selected,
                onClick = {
                    if (choice.end is ForecastEnd.Date) picking = true else shell.forecastEnd = choice.end
                },
                label = { Text(choice.label) },
                modifier = Modifier.semantics { contentDescription = choice.spoken },
            )
        }
    }
    Text(
        "Pick a date up to 12 months ahead.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = AppSpacing.xs, bottom = AppSpacing.md),
    )

    Summary(forecast)
    Spacer(Modifier.height(AppSpacing.sm))
    BalanceLine(forecast)
    ListSection(modifier = Modifier.padding(top = AppSpacing.lg), title = "Everyday spending") {
        item {
            SwitchRow(
                headline = "Count everyday spending",
                supporting = if (forecast.everydayPerDay > 0) {
                    "About ${money.format(forecast.everydayPerDay)} a day, from what you spent in the last ${ForecastFacts.EVERYDAY_DAYS} days " +
                        "outside repeating payments. It is a guess: turn it off to see only what is scheduled."
                } else {
                    "Nothing was spent outside repeating payments in the last ${ForecastFacts.EVERYDAY_DAYS} days, so it adds nothing yet."
                },
                checked = everyday,
                onCheckedChange = { shell.forecastEveryday = it },
            )
        }
    }
    Scheduled(forecast)
    Text(
        "Only what's scheduled is known; everyday spending is an estimate.",
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg),
    )
    Text(
        "Counts cash, bank, wallet and other accounts that hold money. A credit card is counted through its bills, and savings aren't spendable.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.sm, end = AppSpacing.lg, bottom = AppSpacing.xl),
    )

    if (picking) {
        // From tomorrow, to the furthest the forecast looks.
        DatePickerDialogFor(
            initial = end,
            onPicked = {
                picking = false
                shell.forecastEnd = ForecastEnd.Date(it)
            },
            onDismiss = { picking = false },
            latest = forecastLimit(today),
            earliest = today.plusDays(1),
        )
    }
}

/** The money at the end and where it gets to its lowest, as the two panels of the summary banner - and, when it runs out, said in words. */
@Composable
private fun Summary(forecast: Forecast) {
    val money = rememberFinanceState().money
    val last = forecast.last
    val lowest = forecast.lowest
    val lowestDay = if (lowest.date == forecast.today) "today" else "on ${dayLabel(lowest.date)}"
    SplitStatBanner(
        first = BannerFigure(title = "End balance", figure = money.format(last.balance), note = "on ${dayLabel(last.date)}"),
        second = BannerFigure(title = "Lowest point", figure = money.format(lowest.balance), note = lowestDay),
        spoken = "Forecast to ${dayLabel(last.date)}. You would have ${money.format(last.balance)} to spend then. " +
            "The lowest point is ${money.format(lowest.balance)}, $lowestDay.",
    )
    val firstBelow = forecast.points.firstOrNull { it.balance < 0 }
    if (firstBelow != null) {
        Row(
            Modifier.fillMaxWidth().padding(start = AppSpacing.lg, top = AppSpacing.md, end = AppSpacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(AppIconSizes.md),
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                if (firstBelow.date == forecast.today) {
                    "You have nothing left to spend today, and it only gets lower."
                } else {
                    "The money to spend runs out on ${dayLabel(firstBelow.date)}."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The line of the money that can be spent, day by day, in a panel of its own with what it is and how to read a day. */
@Composable
private fun BalanceLine(forecast: Forecast) {
    val money = rememberFinanceState().money
    val scheme = MaterialTheme.colorScheme
    var day by remember { mutableStateOf<Int?>(null) }
    val balances = remember(forecast) { forecast.points.map { it.balance } }
    val last = forecast.last
    val lowest = forecast.lowest
    // The chart says its summary - the lowest and the end, not every day - to a screen reader; with amounts hidden, only the days.
    val spoken = if (money.hidden) {
        "Money you can spend, day by day to ${dayLabel(last.date)}. The lowest point is on ${dayLabel(lowest.date)}. Amounts are hidden."
    } else {
        "Money you can spend, day by day to ${dayLabel(last.date)}. The lowest point is ${money.format(lowest.balance)} on " +
            "${dayLabel(lowest.date)}, and it ends at ${money.format(last.balance)}." +
            if (forecast.goesBelowZero) " It goes below zero." else ""
    }
    ListSection(
        title = "Day by day",
        footer = "The line is the money you could spend each day; the ringed dot is its lowest point" +
            (if (forecast.goesBelowZero) ", and the dashed line is zero" else "") +
            ". Touch or drag along the chart to read a day.",
    ) {
        item {
            Column(Modifier.padding(AppSpacing.lg)) {
                val d = day
                // What the chart points at, above it - it says all this to a screen reader already.
                ChartReadout(
                    label = if (d == null) "On ${dayLabel(last.date)}" else dayLabel(forecast.points[d].date),
                    value = money.format(if (d == null) last.balance else forecast.points[d].balance),
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.height(AppSpacing.lg))
                BalanceChart(
                    balances = balances,
                    semanticsLabel = spoken,
                    lineColor = scheme.primary,
                    belowColor = scheme.error,
                    guideColor = scheme.outlineVariant,
                    ringColor = scheme.surfaceContainer,
                    scrubIndex = day,
                    onScrub = { day = it },
                )
            }
        }
    }
}

/** What is dated in the forecast, a day at a time: the repeating payments, the entries already dated ahead and the cards' bills. */
@Composable
private fun Scheduled(forecast: Forecast) {
    val groups = forecast.items.groupBy { it.date }
    if (groups.isEmpty()) {
        ListSection(modifier = Modifier.padding(top = AppSpacing.lg), title = "What's scheduled") {
            item { ListRow(headline = "Nothing is scheduled in this time", supporting = "Repeating payments, entries dated ahead and card bills show up here.") }
        }
        return
    }
    SectionHeader(
        title = "What's scheduled",
        padding = PaddingValues(start = AppSpacing.lg, top = AppSpacing.xl, end = AppSpacing.lg, bottom = AppSpacing.xs),
    )
    for ((date, items) in groups) {
        ListSection(modifier = Modifier.padding(top = AppSpacing.sm), title = dayHeading(date.atStartOfDay())) {
            for (item in items) item { ScheduledRow(item) }
        }
    }
}

@Composable
private fun ScheduledRow(item: ForecastItem) {
    val finance = rememberFinanceState()
    val category = finance.category(item.categoryId)
    val account = finance.account(item.accountId)
    val to = finance.account(item.toAccountId)
    val bill = item.source == ForecastSource.CardBill
    val title = when {
        bill -> "${item.payee ?: account?.name ?: "Card"} bill"
        item.type == TransactionType.Transfer -> item.payee ?: "Transfer"
        else -> item.payee ?: category?.name ?: item.type?.label.orEmpty()
    }
    val how = when (item.source) {
        ForecastSource.Repeating -> "Repeating"
        ForecastSource.Entered -> "Entered ahead"
        ForecastSource.CardBill -> "Due"
    }
    val supporting = when {
        bill -> "Credit card bill"
        item.type == TransactionType.Transfer -> "${account?.name ?: "Unknown"} → ${to?.name ?: "Unknown"} · $how"
        else -> "${account?.name ?: "Unknown account"} · $how"
    }
    val leading: @Composable () -> Unit = {
        when {
            bill -> LeadingIcon(Icons.Outlined.CreditCard)
            item.type == TransactionType.Transfer -> TransferAvatar()
            else -> CategoryAvatar(category, payee = item.payee)
        }
    }
    val amount: @Composable () -> Unit = {
        TransactionAmount(
            type = if (item.effect > 0) TransactionType.Income else TransactionType.Expense,
            amount = Math.abs(item.effect),
            money = finance.money,
            style = MaterialTheme.typography.titleSmall,
        )
    }
    if (LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE) {
        // At large text the amount goes under the name instead of beside it, where it left the words no room to stay whole.
        Row(Modifier.fillMaxWidth().padding(AppSpacing.lg), verticalAlignment = Alignment.Top) {
            leading()
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                amount()
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        ListRow(headline = title, supporting = supporting, supportingMaxLines = 2, leading = leading, trailing = amount)
    }
}
