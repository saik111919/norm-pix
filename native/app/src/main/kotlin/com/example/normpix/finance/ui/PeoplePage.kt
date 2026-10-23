package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.PeopleSpending
import com.example.normpix.finance.domain.Person
import com.example.normpix.finance.domain.percentLabel
import com.example.normpix.finance.domain.rankedBy
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Which stretch of time the People page adds up. */
private enum class Period(val label: String) { Month("Month"), AllTime("All time"), Custom("Custom") }

private val periodOptions = listOf(
    ChoiceOption(Period.Month, Period.Month.label, Icons.Outlined.CalendarMonth),
    ChoiceOption(Period.AllTime, Period.AllTime.label, Icons.Outlined.AllInclusive),
    ChoiceOption(Period.Custom, Period.Custom.label, Icons.Outlined.DateRange),
)

/**
 * People: what each person has cost you. The total of all spending for the period you pick - a month, everything, or a range
 * of days - and how much of it went on each person, largest first; tap someone for their expenses. A person is only a label
 * on expenses you already recorded, so none of these numbers is new money: they are the same expenses, grouped.
 */
@Composable
fun PeoplePage() {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val now = LocalDateTime.now()

    // The period survives rotation and coming back from a person's page.
    var periodIndex by rememberSaveable { mutableIntStateOf(Period.Month.ordinal) }
    var monthValue by rememberSaveable { mutableIntStateOf(MonthKey.of(now).toInt()) }
    var customStart by rememberSaveable { mutableLongStateOf(0L) }
    var customEnd by rememberSaveable { mutableLongStateOf(0L) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val period = Period.entries[periodIndex]
    val month = MonthKey.fromInt(monthValue)
    val custom = if (customStart != 0L && customEnd > customStart) DateRange(customStart.toUtcDateTime(), customEnd.toUtcDateTime()) else null
    val range: DateRange? = when (period) {
        Period.Month -> month.range
        Period.AllTime -> null
        Period.Custom -> custom
    }
    val periodLabel = when (period) {
        Period.Month -> monthLabel(month)
        Period.AllTime -> "All time"
        Period.Custom -> custom?.let { "${shortDate(it.start)} – ${shortDate(it.end.minusDays(1))}" } ?: "Pick a range"
    }

    val spending by rememberFinanceQuery(range) { peopleSpending(range) }
    val totals = (spending as? AsyncValue.Data)?.value ?: PeopleSpending.Empty
    val loaded = spending is AsyncValue.Data

    val query = search.trim()
    val active = finance.people.filter { !it.archived }
    val archived = finance.people.filter { it.archived }
    val matches = { person: Person -> query.isEmpty() || person.name.contains(query, ignoreCase = true) }
    val shownActive = active.filter(matches).rankedBy(totals)
    val shownArchived = archived.filter(matches).rankedBy(totals)

    AppSubPage(
        title = "People",
        onBack = navigator::back,
        maxWidth = AppLayout.maxListWidth,
        actions = {
            IconButton(onClick = { actions.addPerson() }) { Icon(Icons.Rounded.PersonAdd, contentDescription = "Add person") }
        },
    ) {
        if (finance.people.isEmpty()) {
            item(key = "empty") {
                AppEmptyState(
                    icon = Icons.Outlined.Groups,
                    title = "See what you spend on each person",
                    message = "Add the people you spend on - family, a partner, friends - then pick one when you record an " +
                        "expense. NormPix adds it up for each of them. It only labels expenses: nothing is owed, and nothing " +
                        "else changes.",
                    actionLabel = "Add a person",
                    actionIcon = Icons.Rounded.Add,
                    onAction = { actions.addPerson() },
                )
            }
            return@AppSubPage
        }

        item(key = "period") {
            Column(Modifier.padding(AppSpacing.page)) {
                ConnectedChoiceRow(
                    options = periodOptions,
                    selected = period,
                    onSelect = {
                        periodIndex = it.ordinal
                        if (it == Period.Custom && custom == null) picking = true
                    },
                )
                Spacer(Modifier.height(AppSpacing.md))
                when (period) {
                    Period.Month -> MonthSwitcher(month = month, latest = MonthKey.of(now), onChanged = { monthValue = it.toInt() })
                    Period.Custom -> TonalPickerField(
                        label = "Dates",
                        value = periodLabel,
                        icon = Icons.Outlined.DateRange,
                        onClick = { picking = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Period.AllTime -> Unit
                }
            }
        }

        item(key = "summary") {
            Column(Modifier.padding(AppSpacing.page)) {
                SpendingSummary(totals = totals, periodLabel = periodLabel, loaded = loaded)
            }
        }

        item(key = "search") {
            Column(Modifier.padding(AppSpacing.page).padding(top = AppSpacing.sm)) {
                SearchField(value = search, onValueChange = { search = it }, placeholder = "Search people")
            }
        }

        if (query.isNotEmpty() && shownActive.isEmpty() && shownArchived.isEmpty()) {
            item(key = "no-match") {
                AppEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No one called \"$query\"",
                    message = "Check the spelling, or add them.",
                    actionLabel = "Add \"$query\"",
                    actionIcon = Icons.Rounded.Add,
                    onAction = { actions.addPerson(initialName = query) },
                )
            }
        } else {
            item(key = "people") {
                Column(Modifier.padding(AppSpacing.page)) {
                    ListSection(
                        title = if (query.isEmpty()) "Spent on" else "Matching people",
                        footer = if (query.isEmpty() && totals.unassigned > 0) {
                            "${finance.money.format(totals.unassigned)} in this period isn't for anyone in particular. " +
                                "Pick a person when you record or edit an expense to include it."
                        } else {
                            null
                        },
                    ) {
                        for (person in shownActive) {
                            item {
                                PersonRow(
                                    person = person,
                                    total = totals.of(person.id!!),
                                    share = totals.shareOfPerson(person.id),
                                    onOpen = { navigator.openPerson(person.id) },
                                    onManage = { actions.managePerson(person) },
                                )
                            }
                        }
                        if (query.isEmpty()) {
                            item {
                                ListRow(
                                    headline = "Add person",
                                    leading = { LeadingIcon(Icons.Rounded.Add) },
                                    onClick = { actions.addPerson() },
                                )
                            }
                        }
                    }
                    if (shownArchived.isNotEmpty()) {
                        TextButton(onClick = { showArchived = !showArchived }) {
                            Icon(if (showArchived) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(
                                if (showArchived) "Hide archived people" else "Show ${shownArchived.size} archived ${if (shownArchived.size == 1) "person" else "people"}",
                            )
                        }
                        if (showArchived) {
                            ListSection {
                                for (person in shownArchived) {
                                    item {
                                        PersonRow(
                                            person = person,
                                            total = totals.of(person.id!!),
                                            share = totals.shareOfPerson(person.id),
                                            onOpen = { navigator.openPerson(person.id) },
                                            onManage = { actions.managePerson(person) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        DateRangePickerFor(
            initial = custom,
            latest = now.toLocalDate(),
            onPicked = {
                customStart = it.start.toUtcMillis()
                customEnd = it.end.toUtcMillis()
                periodIndex = Period.Custom.ordinal
                picking = false
            },
            onDismiss = {
                picking = false
                // Cancelling the first pick leaves the page on the period it was on, not on "Custom" with no dates.
                if (custom == null && period == Period.Custom) periodIndex = Period.Month.ordinal
            },
        )
    }
}

/**
 * The period's spending at a glance: the whole of it, how much of that went on people (as a bar and a share) and how much on
 * no one in particular. The whole is every expense in the period - the figure the dashboard shows - never a sum of the parts.
 */
@Composable
private fun SpendingSummary(totals: PeopleSpending, periodLabel: String, loaded: Boolean) {
    val money = rememberFinanceState().money
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = AppRadius.rXl, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.lg)) {
            SummaryStat(
                label = "All spending · $periodLabel",
                value = money.format(totals.totalExpense),
                valueStyle = MaterialTheme.typography.displaySmall,
            )
            if (loaded && totals.totalExpense == 0L) {
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    "Nothing was spent in this period.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(AppSpacing.md))
                AppLinearProgress(Modifier.fillMaxWidth(), progress = { totals.assignedShare.toFloat() })
                Spacer(Modifier.height(AppSpacing.md))
                HeroStats(
                    listOf(
                        { m ->
                            SummaryStat(
                                label = "On people",
                                value = money.format(totals.assigned),
                                modifier = m,
                            )
                        },
                        { m ->
                            SummaryStat(
                                label = "No one in particular",
                                value = money.format(totals.unassigned),
                                modifier = m,
                            )
                        },
                    ),
                )
            }
        }
    }
}

/** One person: their avatar and name, what was spent on them over the period and its share of all spending, with a thin bar. */
@Composable
private fun PersonRow(
    person: Person,
    total: com.example.normpix.finance.domain.PersonTotal?,
    share: Double,
    onOpen: () -> Unit,
    onManage: () -> Unit,
) {
    val money = rememberFinanceState().money
    val amount = total?.amount ?: 0L
    val count = total?.count ?: 0
        val summary = if (count == 0) {
        "Nothing spent in this period"
    } else {
        "$count ${if (count == 1) "expense" else "expenses"} · ${percentLabel(share)} of spending"
    }
    ListItem(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append(person.name)
                    if (person.archived) append(", archived")
                    append(", ").append(if (count == 0) "nothing spent" else "${money.format(amount)} spent, $summary")
                }
                onClick(label = "Open ${person.name}") { onOpen(); true }
                customActions = listOf(CustomAccessibilityAction("Options") { onManage(); true })
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { PersonAvatar(person) },
        headlineContent = { Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(summary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count > 0) {
                    Spacer(Modifier.height(AppSpacing.xs))
                    AppLinearProgress(Modifier.fillMaxWidth().alpha(0.9f), progress = { share.toFloat() }, thickness = 4.dp)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    money.format(amount),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = if (amount == 0L) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                IconButton(onClick = onManage) { Icon(Icons.Rounded.MoreVert, contentDescription = "Options for ${person.name}") }
            }
        },
    )
}

private fun LocalDateTime.toUtcMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toUtcDateTime(): LocalDateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(this), ZoneOffset.UTC)
