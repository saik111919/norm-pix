package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.example.normpix.finance.data.CalendarLedger
import com.example.normpix.finance.data.calendarLedger
import com.example.normpix.finance.domain.BillKind
import com.example.normpix.finance.domain.BillsMonth
import com.example.normpix.finance.domain.CalendarBill
import com.example.normpix.finance.domain.CalendarFacts
import com.example.normpix.finance.domain.CalendarItem
import com.example.normpix.finance.domain.CalendarReminder
import com.example.normpix.finance.domain.CalendarRef
import com.example.normpix.finance.domain.DayMark
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.billsMonth
import com.example.normpix.finance.domain.calendarFirstMonth
import com.example.normpix.finance.domain.calendarLastMonth
import com.example.normpix.finance.domain.defaultDay
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.Reminder
import com.example.normpix.reminders.ReminderCategory
import com.example.normpix.reminders.ReminderLinkKind
import com.example.normpix.reminders.ReminderStatus
import com.example.normpix.reminders.icon
import com.example.normpix.reminders.reminderDueText
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.reminders.rememberResolvedLink
import com.example.normpix.screens.DueBill
import com.example.normpix.screens.rememberDueBills
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The bills calendar page (2026-11-15), opened from Plans > Recurring and from the "Next 7 days" heading on Home: one month at a time,
 * the current one first (up to 12 months ahead and one back), with what is due each day marked on a month grid, the chosen day's things
 * listed under it, and a summary of what goes out and what comes in. The working-out is `billsMonth` (finance/domain); this page
 * gathers the facts once, switches the month without reading anything again, and opens what a tap on a thing is about.
 */

/** Opens as the route does: today's date and the cards' bills as Home shows them. A test brings its own day and bills. */
@Composable
internal fun BillsCalendarPage(today: LocalDate = LocalDate.now(), dueBills: List<DueBill> = rememberDueBills()) {
    val navigator = LocalNavigator.current
    val ledger by rememberFinanceQuery(today) { calendarLedger(today) }
    AppSubPage(title = "Bills calendar", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        item {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = ledger) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Loading your calendar")
                    is AsyncValue.Failure -> AppErrorState(title = "Couldn't load this", message = "Your data is safe on this device. Try again.")
                    is AsyncValue.Data -> BillsCalendarBody(today, value.value, dueBills)
                }
            }
        }
    }
}

@Composable
private fun BillsCalendarBody(today: LocalDate, ledger: CalendarLedger, dueBills: List<DueBill>) {
    val finance = rememberFinanceState()
    val money = finance.money
    val reminders = LocalReminders.current.state.collectAsState().value.reminders
    val billsKey = dueBills.map { it.account.id to it.bill }
    val facts = remember(today, finance.accounts, ledger, billsKey, reminders) {
        CalendarFacts(
            today = today,
            accounts = finance.accounts,
            bills = dueBills.map { CalendarBill(it.account, it.bill) },
            rules = ledger.rules,
            upcoming = ledger.upcoming,
            reminders = reminders.mapNotNull { it.asCalendarReminder() },
        )
    }

    val thisMonth = YearMonth.from(today)
    val firstMonth = calendarFirstMonth(today)
    val lastMonth = calendarLastMonth(today)
    var shown by remember { mutableStateOf(thisMonth) }
    // null = the month's own first choice (today, else its first day with something); a day the user touched stays until the month changes.
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val month = remember(facts, shown) { billsMonth(facts, shown) }
    val day = picked?.takeIf { YearMonth.from(it) == shown } ?: month.defaultDay(today)
    val marks = remember(month) { month.days.associateWith { dayMarksOf(month.on(it)) } }

    val note = if (shown == thisMonth) "From today" else "In ${monthName(shown, today)}"
    SplitStatBanner(
        first = BannerFigure(title = "Due this month", figure = money.format(month.due), note = note),
        second = BannerFigure(title = "Coming in", figure = money.format(month.comingIn), note = note),
        spoken = "Bills calendar, ${monthName(shown, today)}. Due this month ${money.format(month.due)}, ${note.replaceFirstChar(Char::lowercase)}. " +
            "Coming in ${money.format(month.comingIn)}.",
    )

    ListSection(modifier = Modifier.padding(top = AppSpacing.md)) {
        item {
            Column(Modifier.padding(AppSpacing.md)) {
                MonthCalendar(
                    shown = shown,
                    onShown = {
                        shown = it
                        picked = null
                    },
                    first = day,
                    last = day,
                    today = today,
                    latest = lastMonth.atEndOfMonth(),
                    earliest = firstMonth.atDay(1),
                    onPick = { picked = it },
                    markers = marks,
                )
                MarkLegend()
            }
        }
    }

    DayList(month, day, today, reminders)
    Text(
        "A card's bill is counted once: what is charged to a card is in its bill, and a payment onto the card pays it.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.xl),
    )
}

/** A reminder as the calendar needs it; one that was never saved has no id and is not on it. */
private fun Reminder.asCalendarReminder(): CalendarReminder? {
    val id = id ?: return null
    val about = link
    return CalendarReminder(
        id = id,
        title = title,
        dueAt = effectiveDueAt,
        done = status == ReminderStatus.Completed,
        ruleId = about?.takeIf { it.kind == ReminderLinkKind.Recurring }?.id?.toLongOrNull(),
        accountId = about?.takeIf { it.kind == ReminderLinkKind.Account }?.id?.toLongOrNull(),
    )
}

/** What the three kinds of dot mean, in words: the dots are the only picture of a kind in the grid. Read by no one - each day says its own count. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkLegend() {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = AppSpacing.md).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
    ) {
        for ((kind, label) in listOf(DayMark.Out to "Money out", DayMark.In to "Money in", DayMark.Reminder to "Reminder")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DayMarkDot(kind)
                Spacer(Modifier.width(AppSpacing.xs))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The chosen [day]'s things, one row each, under the day's own heading. */
@Composable
private fun DayList(month: BillsMonth, day: LocalDate, today: LocalDate, reminders: List<Reminder>) {
    val actions = rememberFinanceActions()
    val flows = rememberReminderFlows()
    val things = month.on(day)
    ListSection(modifier = Modifier.padding(top = AppSpacing.sm), title = dayHeading(day, today)) {
        if (things.isEmpty()) {
            item {
                ListRow(
                    headline = "Nothing due on this day",
                    supporting = "Card bills, repeating payments, reminders and entries dated ahead show up here.",
                )
            }
        }
        for (thing in things) {
            item {
                when (val ref = thing.ref) {
                    is CalendarRef.Reminder -> {
                        val full = reminders.firstOrNull { it.id == ref.reminder.id }
                        ReminderRow(thing, full, onOpen = { full?.let(flows::edit) })
                    }
                    is CalendarRef.Bill -> MoneyRow(thing, onOpen = { actions.payBill(ref.bill.account, suggested = ref.bill.bill.amount) })
                    is CalendarRef.Rule -> MoneyRow(thing, onOpen = { ref.rule.id?.let(actions::editRecurring) })
                    is CalendarRef.Entry -> MoneyRow(thing, onOpen = { actions.showDetails(ref.transaction) })
                }
            }
        }
    }
}

/** "Today, 10 Mar", "Tomorrow, 11 Mar", else "Wednesday, 12 Mar" (with the year when it is not this one). */
private fun dayHeading(day: LocalDate, today: LocalDate): String {
    val locale = Locale.getDefault()
    val date = DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMM" else "d MMM yyyy", locale).format(day)
    return when (day) {
        today -> "Today, $date"
        today.plusDays(1) -> "Tomorrow, $date"
        else -> "${DateTimeFormatter.ofPattern("EEEE", locale).format(day)}, $date"
    }
}

/** "March", or "March 2027" for another year. */
private fun monthName(month: YearMonth, today: LocalDate): String =
    DateTimeFormatter.ofPattern(if (month.year == today.year) "MMMM" else "MMMM yyyy", Locale.getDefault()).format(month)

/** What a card bill, a repeating payment or an entry is: its icon by kind, its name, the kind in words, and the amount. */
@Composable
private fun MoneyRow(item: CalendarItem, onOpen: () -> Unit) {
    val finance = rememberFinanceState()
    val ref = item.ref
    val account = finance.account(item.accountId)
    val to = finance.account(item.toAccountId)
    val category = finance.category(item.categoryId)
    val type = item.type
    val transfer = type == TransactionType.Transfer
    val bill = ref as? CalendarRef.Bill
    val title = when {
        bill != null -> "${item.title ?: "Card"} bill"
        item.title != null -> item.title
        transfer -> if (item.toCard) "Payment to ${to?.name ?: "a card"}" else "Transfer"
        else -> category?.name ?: type?.label.orEmpty()
    }
    val kind = when {
        bill != null -> "Card bill" + when (bill.bill.bill.kind) {
            BillKind.Overdue -> " · Overdue"
            BillKind.Open -> " · Owed so far"
            BillKind.Statement -> ""
        }
        else -> {
            val what = when {
                transfer && item.toCard -> "card payment"
                transfer -> "transfer"
                type == TransactionType.Income -> "income"
                else -> "payment"
            }
            if (ref is CalendarRef.Rule) "Repeating $what" else "Upcoming $what"
        }
    }
    val where = when {
        bill != null || account == null -> null
        transfer -> "${account.name} → ${to?.name ?: "Unknown"}"
        else -> account.name
    }
    val supporting = listOfNotNull(kind, where, if (item.reminded) "With a reminder" else null).joinToString(" · ")
    CalendarRowFrame(
        headline = title,
        supporting = supporting,
        leading = {
            when {
                bill != null -> LeadingIcon(Icons.Outlined.CreditCard)
                transfer -> TransferAvatar()
                else -> CategoryAvatar(category, payee = item.title)
            }
        },
        trailing = {
            TransactionAmount(
                type = if (bill != null) TransactionType.Expense else type ?: TransactionType.Expense,
                amount = item.amount ?: 0L,
                money = finance.money,
                style = MaterialTheme.typography.titleSmall,
            )
        },
        onClick = onOpen,
    )
}

/** A reminder: its icon, its name, "Reminder" and when it is due - and what it is about, when it is tied to something. */
@Composable
private fun ReminderRow(item: CalendarItem, reminder: Reminder?, onOpen: () -> Unit) {
    val resolved = rememberResolvedLink(reminder?.link)
    val due = reminder?.let { reminderDueText(it) }
    val supporting = listOfNotNull("Reminder", due, resolved?.takeIf { !it.missing }?.line).joinToString(" · ")
    CalendarRowFrame(
        headline = item.title.orEmpty(),
        supporting = supporting,
        leading = { LeadingIcon((reminder?.category ?: ReminderCategory.Personal).icon) },
        trailing = { Text("Reminder", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        onClick = onOpen,
    )
}

/**
 * A row of the day's list. At large text the amount goes under the name instead of beside it, where it left the words no room to stay
 * whole (the same rule as the forecast's rows).
 */
@Composable
private fun CalendarRowFrame(
    headline: String,
    supporting: String,
    leading: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    if (LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(AppSpacing.lg),
            verticalAlignment = Alignment.Top,
        ) {
            leading()
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(headline, style = MaterialTheme.typography.bodyLarge)
                trailing()
                Text(supporting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        ListRow(headline = headline, supporting = supporting, supportingMaxLines = 2, leading = leading, trailing = trailing, onClick = onClick)
    }
}
