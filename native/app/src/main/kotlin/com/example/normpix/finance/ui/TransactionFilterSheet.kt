package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.AccountMark
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.DateRange
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.inTreeOrder
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.domain.startOfDay
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.components.DialogBackdropBlur
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.SeeThroughColors
import com.example.normpix.ui.components.rememberDialogSurface
import com.example.normpix.ui.theme.AppSpacing
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * The advanced filters. Calls [onDone] with the new filters (search text is
 * kept as-is), or null if dismissed.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TransactionFilterSheet(
    current: TransactionQuery,
    showAccounts: Boolean,
    onDone: (TransactionQuery?) -> Unit,
    /** Whether people can be filtered on (hidden on a person's own history, which is already theirs). */
    showPeople: Boolean = true,
) {
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val decimals = finance.currency.decimals

    var types by remember { mutableStateOf(current.types) }
    var accounts by remember { mutableStateOf(current.accountIds) }
    var categories by remember { mutableStateOf(current.categoryIds) }
    var people by remember { mutableStateOf(current.personIds) }
    var range by remember { mutableStateOf(current.range) }
    var min by remember { mutableStateOf(current.minAmount?.let { minorToInputText(it, decimals) }.orEmpty()) }
    var max by remember { mutableStateOf(current.maxAmount?.let { minorToInputText(it, decimals) }.orEmpty()) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var pickingRange by remember { mutableStateOf(false) }

    fun <T> toggle(set: Set<T>, value: T): Set<T> {
        haptics.selection()
        return if (value in set) set - value else set + value
    }

    fun apply() {
        fun read(text: String): Long? = if (text.isBlank()) null else parseMinorUnits(text, decimals)
        val lo = read(min)
        val hi = read(max)
        if ((min.isNotBlank() && lo == null) || (max.isNotBlank() && hi == null)) {
            amountError = "Enter valid amounts"
            return
        }
        if (lo != null && hi != null && lo > hi) {
            amountError = "Minimum is more than maximum"
            return
        }
        onDone(
            current.copy(
                types = types,
                accountIds = accounts,
                categoryIds = categories,
                personIds = people,
                minAmount = lo,
                maxAmount = hi,
                range = range,
            ),
        )
    }

    fun reset() {
        haptics.selection()
        types = emptySet()
        accounts = emptySet()
        categories = emptySet()
        people = emptySet()
        range = null
        min = ""
        max = ""
        amountError = null
    }

    @Composable
    fun Heading(value: String) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = AppSpacing.xl, bottom = AppSpacing.sm),
        )
    }

    /** [current] with this type's categories swapped for [picked]; the other type's stay. A category stands for its sub-categories too. */
    fun replaceCategories(current: Set<Long>, type: CategoryType, picked: Set<Long>): Set<Long> {
        val ofType = finance.categoriesOf(type, includeArchived = true).mapNotNull { it.id }.toSet()
        return (current - ofType) + picked
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.xxl),
    ) {
        SheetHeader(title = "Filter transactions")
        Heading("Type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            for (type in TransactionType.entries) {
                FilterChip(
                    selected = type in types,
                    onClick = { types = toggle(types, type) },
                    label = { Text(type.label) },
                    leadingIcon = { Icon(type.icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        Heading("Dates")
        ListItem(
            modifier = Modifier.clickable { pickingRange = true },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Outlined.DateRange, contentDescription = null) },
            headlineContent = {
                Text(
                    range?.let { "${shortDate(it.start)} – ${shortDate(it.end.minusDays(1))}" } ?: "Month shown on the list",
                )
            },
            supportingContent = { Text(if (range == null) "Tap to choose a custom range" else "Custom range") },
            trailingContent = range?.let {
                {
                    IconButton(onClick = { range = null }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear date range")
                    }
                }
            },
        )
        if (showAccounts) {
            Heading("Accounts")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                for (a in finance.accounts) {
                    FilterChip(
                        selected = a.id in accounts,
                        onClick = { accounts = toggle(accounts, a.id!!) },
                        label = { Text(a.name) },
                        leadingIcon = { AccountMark(a, MaterialTheme.colorScheme.onSurfaceVariant, 18.dp) },
                    )
                }
            }
        }
        if (showPeople && finance.people.isNotEmpty()) {
            Heading("People")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                for (person in finance.people) {
                    FilterChip(
                        selected = person.id in people,
                        onClick = { people = toggle(people, person.id!!) },
                        label = { Text(if (person.archived) "${person.name} (archived)" else person.name) },
                        leadingIcon = { PersonGlyph(person, LocalContentColor.current, 18.dp) },
                    )
                }
            }
        }
        Heading("Categories")
        // A hundred-odd categories do not fit as chips: each type opens the same searchable picker the editor uses.
        CategoryFilterRow(
            title = "Expense categories",
            type = CategoryType.Expense,
            selected = categories.intersect(finance.categoriesOf(CategoryType.Expense, includeArchived = true).mapNotNull { it.id }.toSet()),
            onChange = { picked -> categories = replaceCategories(categories, CategoryType.Expense, picked) },
        )
        CategoryFilterRow(
            title = "Income categories",
            type = CategoryType.Income,
            selected = categories.intersect(finance.categoriesOf(CategoryType.Income, includeArchived = true).mapNotNull { it.id }.toSet()),
            onChange = { picked -> categories = replaceCategories(categories, CategoryType.Income, picked) },
        )
        Heading("Amount")
        // One under the other, each the sheet's full width: side by side they would be too narrow to show a large amount whole.
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AmountField(
                value = min,
                onValueChange = {
                    min = it
                    amountError = null
                },
                currency = finance.currency,
                label = "Minimum",
            )
            AmountField(
                value = max,
                onValueChange = {
                    max = it
                    amountError = null
                },
                currency = finance.currency,
                label = "Maximum",
            )
        }
        FieldError(amountError)
        Spacer(Modifier.height(AppSpacing.xxl))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::reset) { Text("Reset") }
            Spacer(Modifier.weight(1f))
            Button(onClick = ::apply) { Text("Show results") }
        }
    }

    if (pickingRange) {
        DateRangePickerDialogFor(
            initial = range,
            onPicked = {
                range = it
                pickingRange = false
            },
            onDismiss = { pickingRange = false },
        )
    }
}

/**
 * A Material date range picker dialog with frosted styling: a start day and an end day (one tap twice for a single day).
 * [onPicked] gets the whole days from the start of the first to the end of the last, as a [DateRange].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialogFor(initial: DateRange?, onPicked: (DateRange) -> Unit, onDismiss: () -> Unit) {
    val now = LocalDateTime.now()
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initial?.start?.toUtcMillis(),
        initialSelectedEndDateMillis = initial?.end?.minusDays(1)?.let(::startOfDay)?.toUtcMillis(),
        yearRange = 2000..(now.year + 10),
    )
    val preferences = LocalAppPreferences.current
    val prefsState = preferences?.state?.collectAsState()
    val sheetBlurEnabled = prefsState?.value?.sheetBlurEnabled ?: true
    val surface = rememberDialogSurface(enabled = sheetBlurEnabled)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(containerColor = surface.container),
        shape = surface.shape,
        confirmButton = {
            TextButton(
                enabled = pickerState.selectedStartDateMillis != null,
                onClick = {
                    val start = pickerState.selectedStartDateMillis
                    val end = pickerState.selectedEndDateMillis ?: start
                    if (start != null && end != null) {
                        onPicked(DateRange(startOfDay(start.fromUtcMillis()), startOfDay(end.fromUtcMillis()).plusDays(1)))
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DialogBackdropBlur(surface.blurBehind)
        SeeThroughColors(surface.blurBehind, surface.ground) {
            DateRangePicker(state = pickerState, modifier = Modifier.height(500.dp))
        }
    }
}

private fun LocalDateTime.toUtcMillis(): Long = toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.fromUtcMillis(): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
