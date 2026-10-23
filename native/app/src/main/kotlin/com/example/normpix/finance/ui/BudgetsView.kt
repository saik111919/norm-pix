package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.BudgetOverview
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.pathName
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppOverlays
import com.example.normpix.ui.components.AppSnackbars
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.StatusTag
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Monthly budgets for Plan > Budgets: the overall limit and per-category
 * limits for one month, with how much of each is used.
 *
 * Only the current month can be edited. Past months show the budgets that
 * applied then (budgets are versioned by month), so history never changes
 * after the fact.
 */
@Composable
fun BudgetsView() {
    val actions = rememberBudgetActions()
    val navigator = LocalNavigator.current
    val current = MonthKey.of(LocalDateTime.now())
    var month by remember { mutableStateOf(current) }
    val editable = month == current
    val overview by rememberFinanceQuery(month) { budgetOverview(month) }

    Column(Modifier.padding(AppSpacing.page)) {
        // Clear of whatever is above it - the section tabs, or the overdue notice under them - by the same gap either way.
        MonthSwitcher(
            month = month,
            latest = current,
            onChanged = { month = it },
            modifier = Modifier.padding(top = AppSpacing.md),
        )
        when (val value = overview) {
            is AsyncValue.Loading -> AppLoadingState(label = "Loading budgets")
            is AsyncValue.Failure -> AppErrorState(
                title = "Couldn't load this",
                message = "Your data is safe on this device. Try again.",
            )
            is AsyncValue.Data -> {
                val data = value.value
                if (!data.hasAnyBudget) {
                    if (editable) {
                        AppEmptyState(
                            icon = Icons.Outlined.Savings,
                            title = "No budgets yet",
                            message = "Set a monthly limit for all spending, or for the categories you want to keep an eye on.",
                            actionLabel = "Set a budget",
                            actionIcon = Icons.Rounded.Add,
                            onAction = { actions.setOverall() },
                        )
                    } else {
                        AppEmptyState(
                            icon = Icons.Rounded.History,
                            title = "No budget in ${monthLabel(month)}",
                            message = "Budgets you set apply from the current month onward.",
                        )
                    }
                } else {
                    OverallBudget(data, editable, actions)
                    CategoryBudgets(data, editable, actions)
                    if (!editable) {
                        Text(
                            "Past months are kept as they were. Changes apply from ${monthLabel(current)} onward.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.md, end = AppSpacing.lg),
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.lg))
                    TextButton(onClick = navigator::openBudgetHistory) {
                        Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpacing.sm))
                        Text("Budget history")
                    }
                }
            }
        }
    }
}

/** The overall monthly budget, or an invitation to set one. */
@Composable
private fun OverallBudget(overview: BudgetOverview, editable: Boolean, actions: BudgetActions) {
    val finance = rememberFinanceState()
    val status = overview.overall
    if (status == null) {
        ListSection(title = "Overall") {
            item {
                ListRow(
                    headline = "Set a monthly budget",
                    supporting = "Spent ${finance.money.format(overview.totalExpense)} in ${monthLabel(overview.month)}",
                    leading = { LeadingIcon(Icons.Rounded.Add) },
                    enabled = editable,
                    onClick = if (editable) ({ actions.setOverall() }) else null,
                )
            }
        }
        return
    }
    val health = status.health
    ListSection(title = "Overall") {
        item {
            Column(
                Modifier
                    .then(if (editable) Modifier.clickable { actions.setOverall(current = status.allocated) } else Modifier)
                    .padding(AppSpacing.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SummaryStat(
                        label = "Spent in ${monthLabel(overview.month)}",
                        value = finance.money.format(status.spent),
                        valueStyle = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    StatusTag(label = budgetLabel(health), tone = budgetTone(health), icon = budgetIcon(health))
                }
                Spacer(Modifier.height(AppSpacing.md))
                BudgetProgress(status, finance.money)
            }
        }
    }
}

@Composable
private fun CategoryBudgets(overview: BudgetOverview, editable: Boolean, actions: BudgetActions) {
    val finance = rememberFinanceState()
    // Most-used first, so budgets that need attention are at the top.
    val entries = overview.categories.entries.sortedByDescending { it.value.utilization }
    val unbudgeted = finance.categoriesOf(CategoryType.Expense).filter { it.id !in overview.categories }
    ListSection(
        title = "By category",
        footer = if (entries.isEmpty()) {
            "Category budgets are optional. They help with the few categories where spending tends to drift. " +
                "A budget on a category also covers its sub-categories."
        } else {
            "A budget on a category also covers its sub-categories."
        },
    ) {
        for (entry in entries) {
            item {
                CategoryBudgetTile(
                    category = finance.category(entry.key),
                    status = entry.value,
                    onClick = if (editable) ({ actions.setForCategory(categoryId = entry.key, current = entry.value.allocated) }) else null,
                )
            }
        }
        if (editable && unbudgeted.isNotEmpty()) {
            item {
                ListRow(
                    headline = "Add a category budget",
                    leading = { LeadingIcon(Icons.Rounded.Add) },
                    onClick = { actions.setForCategory() },
                )
            }
        }
    }
}

@Composable
private fun CategoryBudgetTile(category: Category?, status: BudgetStatus, onClick: (() -> Unit)?) {
    val finance = rememberFinanceState()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
    ) {
        CategoryAvatar(category)
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    category?.let { finance.categories.pathName(it) } ?: "Deleted category",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    finance.money.format(status.spent),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                )
            }
            Spacer(Modifier.height(AppSpacing.sm))
            BudgetProgress(status, finance.money, calm = true)
        }
    }
}

/** Setting, changing and removing budgets. */
class BudgetActions(
    private val finance: FinanceController,
    private val overlays: AppOverlays,
    private val snackbars: AppSnackbars,
    private val scope: CoroutineScope,
) {
    fun setOverall(current: Long? = null) = edit(categoryId = null, current = current, pickCategory = false)

    /** With no [categoryId], asks which category first. */
    fun setForCategory(categoryId: Long? = null, current: Long? = null) =
        edit(categoryId = categoryId, current = current, pickCategory = categoryId == null)

    private fun edit(categoryId: Long?, current: Long?, pickCategory: Boolean) {
        scope.launch {
            val result = overlays.sheet<BudgetResult> { dismiss ->
                BudgetSheet(categoryId = categoryId, current = current, pickCategory = pickCategory, onDone = dismiss)
            } ?: return@launch
            val month = MonthKey.of(LocalDateTime.now())
            val undo = finance.undoable {
                setBudget(categoryId = result.categoryId, from = month, amount = result.amount)
                emptyList()
            }
            val state = finance.state.value
            val target = if (result.categoryId == null) {
                "Monthly budget"
            } else {
                "${state.category(result.categoryId)?.name ?: "Category"} budget"
            }
            if (result.amount == null) {
                snackbars.offerUndo("$target removed from ${monthLabel(month)} on", finance, undo, scope)
            } else {
                snackbars.show("$target set to ${state.money.format(result.amount, trimZeroFraction = true)}")
            }
        }
    }
}

@Composable
fun rememberBudgetActions(): BudgetActions {
    val finance = LocalFinance.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    return remember(finance, overlays, snackbars, scope) { BudgetActions(finance, overlays, snackbars, scope) }
}

class BudgetResult(val categoryId: Long?, /** Null removes the budget. */ val amount: Long?)

/** Set, change or remove one budget, laid out like the transaction editor (see `EditorSheet`). */
@Composable
private fun BudgetSheet(categoryId: Long?, current: Long?, pickCategory: Boolean, onDone: (BudgetResult?) -> Unit) {
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scheme = MaterialTheme.colorScheme
    val month = MonthKey.of(LocalDateTime.now())
    var chosen by remember { mutableStateOf(categoryId) }
    var amount by remember { mutableStateOf(current?.let { minorToInputText(it, finance.currency.decimals) }.orEmpty()) }
    var categoryError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    val category = finance.category(chosen)
    val title = when {
        pickCategory -> "Category budget"
        category == null -> "Monthly budget"
        else -> "${category.name} budget"
    }
    val choices = if (pickCategory) finance.categoriesOf(CategoryType.Expense) else emptyList()

    fun snapshot() = "$chosen|$amount"
    val initialSnapshot = remember { snapshot() }
    DiscardChangesGuard(dirty = snapshot() != initialSnapshot, isEditing = current != null, keptMessage = "The budget stays as it was.")

    fun save() {
        categoryError = if (pickCategory && chosen == null) "Choose a category" else null
        val parsed = parseMinorUnits(amount, finance.currency.decimals)
        amountError = if (parsed == null || parsed <= 0) "Enter a budget greater than zero" else null
        if (categoryError != null || amountError != null || parsed == null) return
        haptics.success()
        onDone(BudgetResult(chosen, parsed))
    }

    EditorSheet(
        title = title,
        subtitle = "Applies from ${monthLabel(month)} onward. Earlier months keep their budget.",
        saveLabel = if (current == null) "Save budget" else "Save changes",
        onSave = ::save,
        titleTrailing = if (current != null) {
            {
                IconButton(onClick = { onDone(BudgetResult(chosen, null)) }) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove budget", tint = scheme.error)
                }
            }
        } else {
            null
        },
    ) {
        if (choices.isNotEmpty()) {
            TransactionCategoryField(
                categories = choices,
                selected = chosen,
                onSelect = {
                    chosen = it
                    categoryError = null
                },
                onCreate = null,
                errorText = categoryError,
            )
            Spacer(Modifier.height(AppSpacing.xxl))
        }
        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = TransactionType.Expense.role(),
            label = "Limit per month",
            errorText = amountError,
            imeAction = ImeAction.Done,
        )
    }
}

/** Month-by-month budget results, newest first. */
@Composable
fun BudgetHistoryPage() {
    val finance = rememberFinanceState()
    val navigator = LocalNavigator.current
    val until = remember { MonthKey.of(LocalDateTime.now()) }
    val history by rememberFinanceQuery(until) { budgetHistory(until) }
    AppSubPage(title = "Budget history", onBack = navigator::back, maxWidth = AppLayout.maxListWidth) {
        item {
            Column(Modifier.padding(AppSpacing.page)) {
                when (val value = history) {
                    is AsyncValue.Loading -> AppLoadingState(label = "Loading budget history")
                    is AsyncValue.Failure -> AppErrorState(title = "Couldn't load this", message = "Your data is safe on this device. Try again.")
                    is AsyncValue.Data -> if (value.value.isEmpty()) {
                        AppEmptyState(
                            icon = Icons.Rounded.History,
                            title = "No budget history yet",
                            message = "Each month with a budget shows up here, with how it went.",
                        )
                    } else {
                        ListSection(
                            footer = "Months use the overall budget when one was set, otherwise the category budgets added together.",
                        ) {
                            for (entry in value.value) {
                                item {
                                    Column(Modifier.padding(AppSpacing.lg)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                monthLabelFull(entry.month),
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f),
                                            )
                                            StatusTag(
                                                label = budgetLabel(entry.status.health),
                                                tone = budgetTone(entry.status.health),
                                                icon = budgetIcon(entry.status.health),
                                            )
                                        }
                                        Spacer(Modifier.height(AppSpacing.xs))
                                        val count = entry.overview.categories.size
                                        Text(
                                            if (entry.overview.overall != null) {
                                                "Overall budget"
                                            } else {
                                                "$count category ${if (count == 1) "budget" else "budgets"}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.height(AppSpacing.sm))
                                        BudgetProgress(entry.status, finance.money, calm = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
