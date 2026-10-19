package com.example.normpix.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import com.example.normpix.banks.ui.BankBadge
import com.example.normpix.finance.ui.SwipeableTransactionSection
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.ui.AsyncValue
import com.example.normpix.finance.ui.PersonAvatar
import com.example.normpix.finance.ui.TransactionTile
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.finance.ui.rememberFinanceQuery
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.reminders.LocalReminders
import com.example.normpix.reminders.icon
import com.example.normpix.reminders.reminderDueText
import com.example.normpix.reminders.rememberReminderFlows
import com.example.normpix.settings.BackButtonStyle
import com.example.normpix.settings.ui.SettingsCatalog
import com.example.normpix.settings.ui.rememberSettingsActivator
import com.example.normpix.ui.components.BackButton
import com.example.normpix.ui.components.FloatingCircleBackButton
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberBackButtonStyle
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.StoredCard
import kotlinx.coroutines.delay

private const val MAX_TRANSACTIONS = 8
private const val MAX_REMINDERS = 5

/** The Home app-bar button that opens app-wide search. */
@Composable
fun AppSearchButton() {
    val navigator = LocalNavigator.current
    IconButton(onClick = navigator::openSearch) {
        Icon(Icons.Rounded.Search, contentDescription = "Search")
    }
}

/**
 * App-wide search: transactions, accounts, saved cards (by bank and last four
 * digits only, and only while the wallet is unlocked), reminders and settings
 * - all on-device.
 */
@Composable
fun AppSearchPage() {
    val navigator = LocalNavigator.current
    val container = LocalAppContainer.current
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val reminderFlows = rememberReminderFlows()
    val activator = rememberSettingsActivator()
    val remindersState by LocalReminders.current.state.collectAsState()
    val prefs by container.preferences.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(150)
        debounced = query.trim()
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val transactions by rememberFinanceQuery(debounced) {
        if (debounced.isEmpty()) emptyList() else transactions(TransactionQuery(text = debounced), limit = MAX_TRANSACTIONS).items
    }
    // Card list read once per visit, never while the wallet is locked, and not at all while NormPix isn't storing cards.
    val walletLocked = container.lock.isWalletLocked(prefs)
    val storingCards = prefs.walletCardsEnabled
    val cards by produceState(emptyList<StoredCard>(), walletLocked, storingCards) {
        value = if (walletLocked || !storingCards) emptyList() else runCatching { container.vault.loadCards() }.getOrDefault(emptyList())
    }

    val lower = debounced.lowercase()
    val matchedTransactions: List<FinanceTransaction> = (transactions as? AsyncValue.Data)?.value.orEmpty()
    // An account is found by its name or by its bank's ("hdfc" finds the HDFC accounts whatever they are called).
    val accounts = if (lower.isEmpty()) {
        emptyList()
    } else {
        finance.accounts.filter { it.name.lowercase().contains(lower) || finance.catalog.byKey(it.bankKey)?.name?.lowercase()?.contains(lower) == true }
    }
    val people = if (lower.isEmpty()) emptyList() else finance.people.filter { it.name.lowercase().contains(lower) }
    val matchedCards = if (lower.isEmpty()) {
        emptyList()
    } else {
        cards.filter { it.bankName.lowercase().contains(lower) || (debounced.length >= 3 && it.last4.contains(debounced)) }
    }
    val reminders = if (lower.isEmpty()) emptyList() else remindersState.reminders.filter { it.title.lowercase().contains(lower) }.take(MAX_REMINDERS)
    val settings = SettingsCatalog.search(debounced, cardsEnabled = storingCards)
    val nothing = matchedTransactions.isEmpty() && accounts.isEmpty() && people.isEmpty() && matchedCards.isEmpty() && reminders.isEmpty() && settings.isEmpty()

    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
      Box(contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().widthIn(max = AppLayout.maxContentWidth).statusBarsPadding().imePadding(),
        ) {
            // In the top floating circle style the way back is the circle the other pages open with, beside the field.
            val floatingBack = rememberBackButtonStyle() == BackButtonStyle.TopFloatingCircle
            Row(
                Modifier.fillMaxWidth().padding(
                    start = if (floatingBack) AppSpacing.lg else AppSpacing.xs,
                    end = AppSpacing.lg,
                    top = AppSpacing.sm,
                    bottom = AppSpacing.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (floatingBack) {
                    FloatingCircleBackButton(onBack = navigator::back)
                    Spacer(Modifier.width(AppSpacing.sm))
                } else {
                    BackButton(navigator::back)
                }
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search NormPix",
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    start = AppSpacing.lg,
                    end = AppSpacing.lg,
                    bottom = AppSpacing.xl + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                ),
            ) {
                when {
                    debounced.isEmpty() -> item(key = "hint") {
                        Message("Search by payee, note, category, account, person, bank, reminder or setting.")
                    }
                    nothing && transactions !is AsyncValue.Loading -> item(key = "empty") {
                        Message("No results for \"$debounced\"", MaterialTheme.typography.bodyLarge)
                    }
                    else -> {
                        if (matchedTransactions.isNotEmpty()) {
                            item(key = "transactions") {
                                SwipeableTransactionSection(matchedTransactions, title = "Transactions")
                            }
                        }
                        if (accounts.isNotEmpty()) {
                            item(key = "accounts") {
                                ListSection(title = "Accounts") {
                                    for (a in accounts) {
                                        item {
                                            ListRow(
                                                headline = a.name,
                                                supporting = finance.money.format(finance.balanceOf(a.id!!)),
                                                leading = { BankBadge(finance.catalog.byKey(a.bankKey), a.kind.icon) },
                                                onClick = { navigator.openAccountHistory(a.id) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (people.isNotEmpty()) {
                            item(key = "people") {
                                ListSection(title = "People") {
                                    for (p in people) {
                                        item {
                                            ListRow(
                                                headline = p.name,
                                                supporting = if (p.archived) "Archived" else "See what you spend on them",
                                                leading = { PersonAvatar(p) },
                                                onClick = { navigator.openPerson(p.id!!) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (matchedCards.isNotEmpty()) {
                            item(key = "cards") {
                                ListSection(title = "Cards") {
                                    for (card in matchedCards) {
                                        item {
                                            ListRow(
                                                headline = "${card.bankName} •••• ${card.last4}",
                                                supporting = if (card.kind == CardKind.Credit) "Credit card" else "Debit card",
                                                leading = { LeadingIcon(Icons.Outlined.CreditCard) },
                                                onClick = { navigator.go(AppDestination.Wallet) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (reminders.isNotEmpty()) {
                            item(key = "reminders") {
                                ListSection(title = "Reminders") {
                                    for (r in reminders) {
                                        item {
                                            ListRow(
                                                headline = r.title,
                                                supporting = reminderDueText(r),
                                                leading = { LeadingIcon(r.category.icon) },
                                                onClick = { reminderFlows.edit(r) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (settings.isNotEmpty()) {
                            item(key = "settings") {
                                ListSection(title = "Settings") {
                                    for (entry in settings) {
                                        item {
                                            ListRow(
                                                headline = entry.title,
                                                supporting = entry.location,
                                                leading = { Icon(entry.icon, contentDescription = null) },
                                                onClick = { activator.activate(entry) },
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
      }
    }
}

@Composable
private fun Message(text: String, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(
        text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(AppSpacing.xxl),
    )
}


/**
 * The eye at the top of Home and Wallet: hides every amount in the app (as dots) or shows them again. Remembered, so amounts
 * stay hidden until shown, even after NormPix is closed.
 */
@Composable
fun AmountsVisibilityButton() {
    val preferences = LocalAppPreferences.current ?: return
    val hidden = preferences.state.collectAsState().value.amountsHidden
    val haptics = rememberAppHaptics()
    IconButton(
        onClick = {
            haptics.selection()
            preferences.setAmountsHidden(!hidden)
        },
    ) {
        Icon(
            if (hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = if (hidden) "Show amounts" else "Hide amounts",
        )
    }
}
