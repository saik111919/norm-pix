package com.example.normpix.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.FinancialYear
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TagReviewRow
import com.example.normpix.finance.domain.settledCutoff
import com.example.normpix.finance.domain.tagReview
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.AppPreferences
import com.example.normpix.settings.YearStart
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.OptionSheet
import com.example.normpix.ui.components.OptionSheetItem
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/*
 * The yearly review by tag (2026-11-15, round-2 recommendation 8): for a financial year - April to March, or January to December as
 * the user chooses (Settings > Finance > Year starts in) - every tag with what its expenses and its income came to and how many
 * transactions that is, the tax-deductible tag first. As of today, as every figure is: an entry dated ahead counts from its own day.
 * Integer paise throughout; transfers carry no tags, so they are never in it.
 */

/** The title of the year-start setting, on its Settings row, its sheet and its search entry. */
const val YEAR_START_TITLE = "Year starts in"

/** Test tag of the year switcher. */
const val YEAR_SWITCHER_TAG = "year_switcher"

/** The page: Insights > Year by tag, and a tag's own history (its calendar button). */
@Composable
fun YearByTagPage() {
    val navigator = LocalNavigator.current
    val finance = rememberFinanceState()
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val overlays = LocalOverlays.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val startMonth = prefs.yearStart.month
    val today = LocalDate.now()
    val current = FinancialYear.containing(today, startMonth)
    // How many years back from this one: kept when the year's start is changed, so the year shown stays "this year" or "last year".
    var back by remember { mutableIntStateOf(0) }
    val year = current.copy(startYear = current.startYear - back)
    val firstMonth by rememberFinanceQuery(Unit) { firstActiveMonth() }
    val earliest = (firstMonth as? AsyncValue.Data)?.value?.let { FinancialYear.containing(it.start.toLocalDate(), startMonth) } ?: current
    val asOf = finance.asOf ?: settledCutoff(LocalDateTime.now())
    val totals by rememberFinanceQuery(year to asOf) { tagTotals(year.range, asOf) }

    AppSubPage(title = "Year by tag", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        item(key = "year") {
            Column(Modifier.padding(AppSpacing.page)) {
                YearSwitcher(
                    year = year,
                    isCurrent = back == 0,
                    canGoBack = year > earliest,
                    canGoForward = back > 0,
                    onBack = {
                        haptics.selection()
                        back++
                    },
                    onForward = {
                        haptics.selection()
                        if (back > 0) back--
                    },
                    modifier = Modifier.widthIn(max = 480.dp).align(Alignment.CenterHorizontally),
                )
            }
        }
        item(key = "tags") {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = totals) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Loading the year")
                    is AsyncValue.Failure -> AppErrorState(title = "Couldn't load this", message = "Your data is safe on this device. Try again.")
                    is AsyncValue.Data -> {
                        val rows = tagReview(finance.tags, value.value)
                        if (rows.isEmpty()) {
                            AppEmptyState(
                                icon = Icons.AutoMirrored.Outlined.Label,
                                title = "No tags yet",
                                message = "Put tags such as Tax-deductible or Reimbursable on expenses and income, in the Tags field of a " +
                                    "transaction, and see here what each came to over the year.",
                            )
                        } else {
                            val tagged = rows.count { it.total.count > 0 }
                            ListSection(
                                title = "By tag",
                                intro = if (tagged == 0) "Nothing was tagged in ${year.label}${if (back == 0) " so far" else ""}." else null,
                                footer = "A transaction with two tags counts under both. Transfers have no tags; an entry dated after today " +
                                    "counts from its own day. Tap a tag to see its transactions.",
                            ) {
                                for (row in rows) item {
                                    TagYearRow(row, finance.money, onOpen = { row.tag.id?.let { navigator.openTagHistory(it, year.range) } })
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "setting") {
            Column(Modifier.padding(AppSpacing.page)) {
                ListSection(title = "Year") {
                    item {
                        ListRow(
                            headline = YEAR_START_TITLE,
                            supporting = "${prefs.yearStart.label} · ${prefs.yearStart.description}",
                            leading = { LeadingIcon(Icons.Outlined.CalendarMonth) },
                            onClick = { scope.launch { pickYearStart(overlays, preferences) } },
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.lg))
            }
        }
    }
}

/**
 * One tag's year: its name, "Spent ₹12,400.00 · Received ₹3,000.00" and how many transactions, or that there was nothing. The amounts
 * are under the name, not beside it, so they stay whole at any text size; hidden amounts are hidden ([MoneyFormatter]).
 */
@Composable
private fun TagYearRow(row: TagReviewRow, money: MoneyFormatter, onOpen: () -> Unit) {
    val total = row.total
    // At large text the two figures go one under the other: side by side at 1.6x the line broke after its dot (seen in a render).
    val between = if (LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE) "\n" else " · "
    val supporting = if (total.count == 0) {
        "Nothing this year"
    } else {
        "Spent ${money.format(total.spent)}${between}Received ${money.format(total.received)}\n" +
            if (total.count == 1) "1 transaction" else "${total.count} transactions"
    }
    ListRow(
        headline = row.tag.name,
        supporting = supporting,
        supportingMaxLines = 4,
        leading = { LeadingIcon(Icons.AutoMirrored.Outlined.Label) },
        trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        onClick = onOpen,
    )
}

/**
 * The year shown, in the family of the month and day switchers: a step back, the year on its filled pill ("FY 2026-27" over "Apr 2026 –
 * Mar 2027", and "So far" while it is this one), and a step forward - dimmed at this year, as nothing after today counts yet - and
 * back no further than the year of the first transaction.
 */
@Composable
fun YearSwitcher(
    year: FinancialYear,
    isCurrent: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val under = if (isCurrent) "${year.span} · so far" else year.span
    SwitcherTrack(modifier.fillMaxWidth().testTag(YEAR_SWITCHER_TAG)) {
        StepButton(Icons.Rounded.ChevronLeft, "Previous year", enabled = canGoBack, onClick = onBack)
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clearAndSetSemantics {
                    contentDescription = "${year.label}, ${year.span}${if (isCurrent) ", so far" else ""}"
                    // Stepping a year moves nothing a screen reader is on: the new year is announced as it arrives.
                    liveRegion = LiveRegionMode.Polite
                },
            shape = CircleShape,
            color = scheme.secondaryContainer,
            contentColor = scheme.onSecondaryContainer,
        ) {
            Column(
                Modifier.padding(horizontal = AppSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val type = MaterialTheme.typography.titleMedium
                Text(
                    year.label,
                    style = type,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = type.fontSize),
                    overflow = TextOverflow.Ellipsis,
                )
                // Shrinks to fit rather than losing its end ("· so far") at large text, as the year's name above it does.
                val small = MaterialTheme.typography.labelMedium
                Text(
                    under,
                    style = small,
                    maxLines = 1,
                    softWrap = false,
                    autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = small.fontSize),
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        StepButton(Icons.Rounded.ChevronRight, "Next year", enabled = canGoForward, onClick = onForward)
    }
}

/**
 * Asks where the year starts (April, India's financial year, or January) in an option sheet, and keeps the answer. The one way it is
 * chosen, from Settings > Finance and from the Year by tag page alike. True when it changed.
 */
suspend fun pickYearStart(overlays: AppOverlays, preferences: AppPreferences): Boolean {
    val current = preferences.state.value.yearStart
    val chosen = overlays.sheet<YearStart> { dismiss ->
        OptionSheet(
            title = YEAR_START_TITLE,
            subtitle = "Where a year of Year by tag begins",
            options = YearStart.entries.map { OptionSheetItem(it, it.label, it.description) },
            selected = setOf(current),
            onSelect = { dismiss(it) },
        )
    } ?: return false
    if (chosen == current) return false
    preferences.setYearStart(chosen)
    return true
}

/**
 * One tag's transactions, pushed from a tag chip (all time) or from a tag's row of the year by tag (that year's days): the history with
 * the tag as a filter - and the days as one - that can be taken off like any other, and the way on to the year by tag.
 */
@Composable
fun TagHistoryPage(tagId: Long, range: DateRange? = null) {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    var allTime by remember { mutableStateOf(range == null) }
    TransactionHistory(
        title = finance.tag(tagId)?.name ?: "Tag",
        onBack = navigator::back,
        actions = {
            IconButton(onClick = navigator::openYearByTag) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = "Year by tag")
            }
        },
        initialMonth = range?.let { MonthKey.of(it.start) },
        initialFilters = TransactionQuery(range = range, tagIds = setOf(tagId)),
        allTime = allTime,
        onAllTimeChange = { allTime = it },
    )
}

/** Insights' way on to the year by tag: one row under its own heading, saying what it shows (or how to start). */
@Composable
internal fun YearByTagLink() {
    val navigator = LocalNavigator.current
    val finance = rememberFinanceState()
    val start = (LocalAppContainer.current.preferences.state.collectAsState().value.yearStart).month
    val year = FinancialYear.containing(LocalDate.now(), start)
    ListSection(title = "By tag") {
        item {
            ListRow(
                headline = "Year by tag",
                supporting = if (finance.tags.isEmpty()) {
                    "Tag expenses and income, such as Tax-deductible, and see what each came to over the year"
                } else {
                    "What each tag came to in ${year.label}"
                },
                leading = { LeadingIcon(Icons.AutoMirrored.Outlined.Label) },
                trailing = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
                onClick = navigator::openYearByTag,
            )
        }
    }
}
