package com.example.normpix.settings.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.AccountMark
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.settings.TransactionSwipeActions
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalAppScope
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.RadioRow
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SwitchRow
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Settings > Finance: currency, the default account for new entries, and categories. */
@Composable
fun FinanceSettingsPage() {
    val navigator = LocalNavigator.current
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val scope = LocalAppScope.current
    val currency = finance.currency
    val preferences = LocalAppContainer.current.preferences
    val prefs by preferences.state.collectAsState()
    val haptics = rememberAppHaptics()
    val expense = finance.categoriesOf(CategoryType.Expense).size
    val income = finance.categoriesOf(CategoryType.Income).size

    fun pickCurrency() {
        scope.launch {
            val picked = overlays.sheet<CurrencySpec> { dismiss -> CurrencySheet(current = currency, onPick = dismiss) }
            if (picked == null || picked.code == controller.state.value.currency.code) return@launch
            try {
                controller.setCurrency(picked.code)
                snackbars.show("Currency set to ${picked.name}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: FinanceConflictException) {
                overlays.alert("Can't switch to this currency", e.message)
            } catch (e: FinanceValidationException) {
                overlays.alert("Can't switch to this currency", e.message)
            } catch (_: Exception) {
                snackbars.show("Couldn't change the currency. Try again.")
            }
        }
    }

    fun pickDefaultAccount() {
        scope.launch {
            val state = controller.state.value
            val picked = overlays.sheet<Account> { dismiss ->
                DefaultAccountSheet(accounts = state.activeAccounts, currentId = state.defaultAccount?.id, onPick = dismiss)
            } ?: return@launch
            try {
                controller.setDefaultAccountId(picked.id)
                snackbars.show("${picked.name} is now the default")
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Couldn't change the default account. Try again.")
            }
        }
    }

    AppSubPage(title = "Finance", onBack = navigator::back) {
        item(key = "body") {
            Column(Modifier.padding(AppSpacing.page)) {
                ListSection(
                    title = "Money",
                    footer = "Amounts are stored exactly, in whole units of the currency's smallest denomination.",
                ) {
                    item {
                        ListRow(
                            headline = "Currency",
                            supporting = "${currency.name} (${currency.symbol.trim()})",
                            leading = { Icon(Icons.Rounded.CurrencyExchange, contentDescription = null) },
                            onClick = ::pickCurrency,
                        )
                    }
                    item {
                        ListRow(
                            headline = "Default account",
                            supporting = finance.defaultAccount?.name ?: "None",
                            leading = { Icon(Icons.Rounded.StarOutline, contentDescription = null) },
                            enabled = finance.activeAccounts.isNotEmpty(),
                            onClick = ::pickDefaultAccount,
                        )
                    }
                }
                ListSection(
                    title = "Lists",
                    footer = "Swiping is only a shortcut. Edit and delete are always in a transaction's details, and Reminders keep their menu.",
                ) {
                    item {
                        SwitchRow(
                            checked = prefs.swipeActionsEnabled,
                            onCheckedChange = {
                                haptics.selection()
                                preferences.setSwipeActionsEnabled(it)
                            },
                            headline = "Swipe actions",
                            supporting = "Swipe a transaction or reminder sideways to reach its actions.",
                            leading = { Icon(Icons.Rounded.SwipeLeft, contentDescription = null) },
                        )
                    }
                }
                RevealVisibility(visible = prefs.swipeActionsEnabled) {
                    ListSection(title = "Swiping a transaction shows", modifier = Modifier.selectableGroup()) {
                        for (choice in TransactionSwipeActions.entries) {
                            item {
                                RadioRow(
                                    headline = choice.label,
                                    selected = prefs.transactionSwipeActions == choice,
                                    onClick = {
                                        haptics.selection()
                                        preferences.setTransactionSwipeActions(choice)
                                    },
                                )
                            }
                        }
                    }
                }
                ListSection(title = "Organize") {
                    item {
                        ListRow(
                            headline = "Categories",
                            supporting = "$expense for spending · $income for income",
                            leading = { Icon(Icons.Outlined.Category, contentDescription = null) },
                            onClick = navigator::openCategories,
                        )
                    }
                    item {
                        ListRow(
                            headline = "People",
                            supporting = if (finance.people.isEmpty()) {
                                "See what you spend on each person"
                            } else {
                                "${finance.activePeople.size} ${if (finance.activePeople.size == 1) "person" else "people"} · what you spend on each"
                            },
                            leading = { Icon(Icons.Outlined.Groups, contentDescription = null) },
                            onClick = navigator::openPeople,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencySheet(current: CurrencySpec, onPick: (CurrencySpec?) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
            SheetHeader(
                title = "Currency",
                subtitle = "Changes how amounts are shown. Existing amounts are not converted.",
            )
        }
        Column(Modifier.selectableGroup()) {
            for (c in CurrencySpec.all) {
                RadioRow(
                    headline = c.name,
                    supporting = "${c.code} · ${c.symbol.trim()}",
                    selected = c.code == current.code,
                    onClick = { onPick(c) },
                    modifier = Modifier.padding(horizontal = AppSpacing.sm),
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
    }
}

@Composable
private fun DefaultAccountSheet(accounts: List<Account>, currentId: Long?, onPick: (Account?) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Box(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
            SheetHeader(title = "Default account", subtitle = "Pre-selected when you add a transaction")
        }
        Column(Modifier.selectableGroup()) {
            for (a in accounts) {
                RadioRow(
                    headline = a.name,
                    supporting = a.kind.label,
                    selected = a.id == currentId,
                    onClick = { onPick(a) },
                    trailing = { AccountMark(a, MaterialTheme.colorScheme.onSurfaceVariant, 24.dp) },
                    modifier = Modifier.padding(horizontal = AppSpacing.sm),
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
    }
}
