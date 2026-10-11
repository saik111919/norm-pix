package com.example.normpix.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.creditRoom
import com.example.normpix.finance.ui.ChoiceOption
import com.example.normpix.finance.ui.ConnectedChoiceRow
import com.example.normpix.finance.ui.FinanceStatus
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.SummaryStat
import com.example.normpix.finance.ui.TABULAR_FIGURES
import com.example.normpix.finance.ui.icon
import com.example.normpix.finance.ui.label
import com.example.normpix.finance.ui.rememberFinanceActions
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.navigation.LocalTabVisible
import com.example.normpix.ui.components.AnimatedEntrance
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppErrorState
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.AppPage
import com.example.normpix.banks.Bank
import com.example.normpix.banks.ui.BankBadge
import com.example.normpix.ui.components.BankGlyph
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.LockGate
import com.example.normpix.settings.AccountsLayout
import com.example.normpix.settings.CardsLayout
import com.example.normpix.settings.BankIconStyle
import com.example.normpix.ui.components.SectionHeader
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.RevealVisibility
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppIconSizes
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.rememberReducedMotion
import com.example.normpix.wallet.AccountCardData
import com.example.normpix.wallet.AccountCardView
import com.example.normpix.wallet.AccountStack
import com.example.normpix.wallet.CardKind
import com.example.normpix.wallet.AddTile
import com.example.normpix.wallet.CardStack
import com.example.normpix.wallet.CardsState
import com.example.normpix.wallet.WalletCardView
import com.example.normpix.wallet.expiry.CardExpiryNotice
import com.example.normpix.wallet.motion.rememberCardTilt
import com.example.normpix.wallet.nfc.ExchangeTab
import com.example.normpix.wallet.rememberCardFlows
import com.example.normpix.wallet.rememberCardReveal
import kotlinx.coroutines.launch

/**
 * The Wallet tab: money accounts (with balances computed from the ledger) and
 * saved cards.
 *
 * Cards live in an encrypted, local-only vault and nothing here touches the
 * network. When the wallet lock is on, the whole tab stays behind [LockGate]
 * until the user authenticates.
 *
 * @param active whether the tab is the one on screen; each new visit may
 *   prompt for unlock once.
 */
@Composable
fun WalletScreen(listState: LazyListState = rememberLazyListState(), active: Boolean = true) {
    val container = LocalAppContainer.current
    val prefs by container.preferences.state.collectAsState()
    val lockVersion by container.lock.version.collectAsState()
    val cardsState by container.cards.state.collectAsState()
    val snackbars = LocalSnackbars.current
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val flows = rememberCardFlows()
    val catalog = rememberFinanceState().catalog

    var autoPrompted by remember { mutableStateOf(false) }
    var showArchived by remember { mutableStateOf(false) }
    var bankFilter by remember { mutableStateOf<String?>(null) }
    // What is showing its number and CVV, and the NFC-tag step in front of showing it: shared with the card sheet on Home.
    val reveal = rememberCardReveal(flows)
    val revealed = reveal.revealed

    LaunchedEffect(Unit) { if (container.cards.state.value is CardsState.Loading) container.cards.load() }

    // Expiry reminders follow the card list. The app keeps them in step on its own (so they work without this tab
    // ever opening); doing it here too covers the sample-data cards, which have their own, silent tracker.
    LaunchedEffect(cardsState, container.expiry) {
        (cardsState as? CardsState.Ready)?.let { container.expiry.sync(it.cards) }
    }
    val expiryStates by container.expiry.states.collectAsState()
    var notificationsOn by remember { mutableStateOf(container.expiry.notificationsEnabled()) }
    LifecycleResumeEffect(container.expiry) {
        notificationsOn = container.expiry.notificationsEnabled() // may have been changed in system settings
        onPauseOrDispose {}
    }

    @Suppress("UNUSED_EXPRESSION")
    lockVersion // re-evaluate the lock whenever it may have changed
    val locked = container.lock.isWalletLocked(prefs)

    // Cards lean with the phone only while they can be seen: this tab in front, unlocked, and the setting on.
    val tilt = rememberCardTilt(enabled = prefs.cardTiltEnabled && LocalTabVisible.current && !locked)

    fun attemptUnlock() {
        scope.launch {
            if (container.lock.authenticate("Unlock your wallet")) {
                autoPrompted = false
            } else {
                snackbars.show("Couldn't unlock. Try again.")
            }
        }
    }

    // Each new visit to a locked wallet prompts once on its own.
    LaunchedEffect(active) { if (active) autoPrompted = false }
    LaunchedEffect(active, locked, autoPrompted) {
        if (active && locked && !autoPrompted) {
            autoPrompted = true
            if (container.lock.isDeviceSupported()) attemptUnlock()
        }
    }

    if (locked) {
        AppPage(title = "Wallet", listState = listState, insetBottom = false) {
            item {
                LockGate(
                    title = "Wallet is locked",
                    message = "Unlock with your fingerprint, face or screen lock to see your accounts and cards.",
                    unlockLabel = "Unlock",
                    onUnlock = ::attemptUnlock,
                    modifier = Modifier.height(480.dp),
                )
            }
        }
        return
    }

    AppPage(
        title = "Wallet",
        actions = {
            IconButton(onClick = { flows.openNfcExchange(ExchangeTab.Inlet) }) {
                Icon(Icons.Outlined.Nfc, contentDescription = "NFC Card Exchange (Inlet / Outlet)")
            }
        },
        listState = listState,
        bottomPadding = AppLayout.fabClearance,
        insetBottom = false,
    ) {
        item(key = "accounts") {
            Column(Modifier.padding(AppSpacing.page)) {
                // How many cards each bank has: the other half of the account-to-bank link, seen from the account.
                val cardsPerBank = (cardsState as? CardsState.Ready)?.cards.orEmpty()
                    .mapNotNull { it.bankKey }.groupingBy { it }.eachCount()
                AccountsSection(
                    cardsPerBank = cardsPerBank,
                    showArchived = showArchived,
                    onToggleArchived = { showArchived = !showArchived },
                    layout = prefs.accountsLayout,
                    onLayout = container.preferences::setAccountsLayout,
                    bankIconStyle = prefs.bankIconStyle,
                )
            }
        }
        item(key = "cards-header") {
            Box(Modifier.padding(AppSpacing.page)) {
                SectionHeader(
                    title = "Cards",
                    actionLabel = "Add card",
                    onAction = flows::addCard,
                    padding = androidx.compose.foundation.layout.PaddingValues(
                        start = AppSpacing.lg,
                        top = AppSpacing.xxl,
                        end = AppSpacing.sm,
                        bottom = AppSpacing.sm,
                    ),
                )
            }
        }
        when (val state = cardsState) {
            is CardsState.Loading -> item(key = "cards-loading") { AppLoadingState(label = "Loading cards") }
            is CardsState.Failed -> item(key = "cards-failed") {
                AppErrorState(
                    title = "Couldn't open the card vault",
                    message = "Your cards are still stored on this device. Try again, or restart the app.",
                    onRetry = { scope.launch { container.cards.load() } },
                )
            }
            is CardsState.Ready -> {
                val cards = state.cards
                if (cards.isEmpty()) {
                    item(key = "cards-empty") {
                        Box(Modifier.padding(AppSpacing.page)) {
                            ListSection {
                                item {
                                    ListRow(
                                        headline = "Add your first card",
                                        supporting = "Card details are encrypted and stay on this device.",
                                        leading = { LeadingIcon(Icons.Outlined.AddCard) },
                                        onClick = flows::addCard,
                                    )
                                }
                                item {
                                    ListRow(
                                        headline = "Tap to add (NFC)",
                                        supporting = "Tap a phone sharing a card from NormPix, or your contactless card.",
                                        leading = { LeadingIcon(Icons.Outlined.Nfc) },
                                        onClick = { flows.openNfcExchange(ExchangeTab.Inlet) },
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // The banks the cards belong to - the same banks the accounts name, not names read off the cards.
                    val banks = cards.mapNotNull { catalog.byKey(it.bankKey) }.distinct()
                    // The filtered bank may have just lost its last card.
                    val activeFilter = bankFilter?.takeIf { key -> banks.any { it.key == key } }
                    val visible = if (activeFilter == null) cards else cards.filter { it.bankKey == activeFilter }
                    item(key = "cards-layout") {
                        // The same cards three ways, as the accounts above; the choice is the Wallet's own (Settings has it too).
                        Box(Modifier.padding(horizontal = AppSpacing.lg).padding(bottom = AppSpacing.md)) {
                            ConnectedChoiceRow(
                                options = cardsLayoutOptions,
                                selected = prefs.cardsLayout,
                                onSelect = {
                                    haptics.selection()
                                    container.preferences.setCardsLayout(it)
                                },
                            )
                        }
                    }
                    if (banks.size > 1) {
                        item(key = "cards-filter") {
                            BankFilter(
                                banks = banks,
                                selected = activeFilter,
                                onSelected = {
                                    haptics.selection()
                                    bankFilter = it
                                },
                            )
                        }
                    }
                    if (prefs.cardsLayout != CardsLayout.Cards) {
                        // A card's expiry needs saying whichever way the cards are laid out.
                        if (visible.any { expiryStates[it.id]?.needsAttention == true }) {
                            item(key = "cards-notices") {
                                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                                    for (card in visible) {
                                        val expiry = expiryStates[card.id]?.takeIf { it.needsAttention } ?: continue
                                        CardExpiryNotice(
                                            state = expiry,
                                            card = "${card.bankName} •••• ${card.last4}",
                                            notificationsOn = notificationsOn,
                                            onMarkDone = {
                                                haptics.success()
                                                container.expiry.markDone(card.id)
                                            },
                                            onTurnOnNotifications = flows::turnOnExpiryNotifications,
                                            modifier = Modifier.padding(bottom = AppSpacing.md),
                                        )
                                    }
                                }
                            }
                        }
                        if (prefs.cardsLayout == CardsLayout.Stack) {
                            item(key = "cards-stack") {
                                Column(Modifier.padding(horizontal = AppSpacing.lg).padding(bottom = AppSpacing.lg)) {
                                    CardStack(cards = visible, onOpen = flows::showCard, bankIconStyle = prefs.bankIconStyle)
                                    // The next card goes where this stack ends: a slim tile, as the cards behind the front one are slim.
                                    Spacer(Modifier.height(AppSpacing.lg))
                                    AddTile(title = "Add card", supporting = "Encrypted, and kept on this device", onClick = flows::addCard, compact = true)
                                }
                            }
                        } else {
                            item(key = "cards-rows") {
                                Box(Modifier.padding(horizontal = AppSpacing.lg).padding(bottom = AppSpacing.lg)) {
                                    ListSection {
                                        for (card in visible) {
                                            item {
                                                ListRow(
                                                    headline = "${card.bankName} •••• ${card.last4}",
                                                    supporting = "${if (card.kind == CardKind.Credit) "Credit card" else "Debit card"} · Expires ${card.expiryMonth}/${card.expiryYear}",
                                                    leading = { BankBadge(catalog.byKey(card.bankKey), Icons.Outlined.CreditCard) },
                                                    onClick = { flows.showCard(card) },
                                                )
                                            }
                                        }
                                        item {
                                            ListRow(
                                                headline = "Add card",
                                                leading = { LeadingIcon(Icons.Rounded.Add) },
                                                onClick = flows::addCard,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                      visible.forEachIndexed { index, card ->
                        item(key = "card-${card.id}") {
                            Box(Modifier.animateItem().fillMaxWidth().padding(horizontal = AppSpacing.lg).padding(bottom = AppSpacing.lg), contentAlignment = Alignment.Center) {
                                // Card art keeps a card's proportions; cap it on wide windows.
                                Box(Modifier.widthIn(max = 440.dp)) {
                                    AnimatedEntrance(delayMs = 40L * index) {
                                        Column {
                                            val expiry = expiryStates[card.id]?.takeIf { it.needsAttention }
                                            // Keeps showing what it showed while the notice eases out after "Mark as done".
                                            var lastShown by remember { mutableStateOf(expiry) }
                                            if (expiry != null) lastShown = expiry
                                            RevealVisibility(visible = expiry != null) {
                                                lastShown?.let { shown ->
                                                    CardExpiryNotice(
                                                        state = shown,
                                                        card = "${card.bankName} •••• ${card.last4}",
                                                        notificationsOn = notificationsOn,
                                                        onMarkDone = {
                                                            haptics.success()
                                                            container.expiry.markDone(card.id)
                                                        },
                                                        onTurnOnNotifications = flows::turnOnExpiryNotifications,
                                                        modifier = Modifier.padding(bottom = AppSpacing.md),
                                                    )
                                                }
                                            }
                                            WalletCardView(
                                                card = card,
                                                revealed = card.id in revealed,
                                                actionStyle = prefs.walletCardActionStyle,
                                                nfcGated = prefs.nfcTagRevealEnabled,
                                                bankIconStyle = prefs.bankIconStyle,
                                                onToggleReveal = { reveal.toggle(card.id) },
                                                onShareNfc = { flows.openNfcExchange(ExchangeTab.Outlet, card) },
                                                onEdit = { flows.editCard(card) },
                                                onDelete = {
                                                    revealed.remove(card.id)
                                                    flows.deleteCard(card)
                                                },
                                                tilt = tilt,
                                                onCopy = { field -> reveal.copy(card, field) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                      }
                      // The next card is one more of them, drawn empty, where it would go.
                      item(key = "cards-add") {
                          Box(Modifier.animateItem().fillMaxWidth().padding(horizontal = AppSpacing.lg).padding(bottom = AppSpacing.lg), contentAlignment = Alignment.Center) {
                              Box(Modifier.widthIn(max = 440.dp)) {
                                  AddTile(title = "Add card", supporting = "Encrypted, and kept on this device", onClick = flows::addCard)
                              }
                          }
                      }
                    }
                }
            }
        }
    }
}

private val cardsLayoutOptions = listOf(
    ChoiceOption(CardsLayout.Stack, "Stack", Icons.Outlined.Layers),
    ChoiceOption(CardsLayout.Cards, "Cards", Icons.Outlined.ViewAgenda),
    ChoiceOption(CardsLayout.Rows, "Rows", Icons.AutoMirrored.Outlined.ViewList),
)

private val accountsLayoutOptions = listOf(
    ChoiceOption(AccountsLayout.Stack, "Stack", Icons.Outlined.Layers),
    ChoiceOption(AccountsLayout.Cards, "Cards", Icons.Outlined.ViewAgenda),
    ChoiceOption(AccountsLayout.Rows, "Rows", Icons.AutoMirrored.Outlined.ViewList),
)

/** Total balance and the accounts, laid out as the Wallet's [layout] says. */
@Composable
private fun AccountsSection(
    cardsPerBank: Map<String, Int>,
    showArchived: Boolean,
    onToggleArchived: () -> Unit,
    layout: AccountsLayout,
    onLayout: (AccountsLayout) -> Unit,
    bankIconStyle: BankIconStyle,
) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    when (finance.status) {
        FinanceStatus.Loading -> {
            AppLoadingState(label = "Loading accounts")
            return
        }
        FinanceStatus.Failed -> {
            AppErrorState(
                title = "Couldn't load your accounts",
                message = "Your data is safe on this device. Try again.",
                onRetry = { scope.launch { controller.load() } },
            )
            return
        }
        FinanceStatus.Ready -> Unit
    }
    val active = finance.activeAccounts
    val archived = finance.accounts.filter { it.archived }

    Column {
        SummaryStat(
            label = "Total balance",
            value = finance.money.format(finance.totalBalance),
            valueStyle = MaterialTheme.typography.displaySmall,
            modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.sm),
        )
        // The total is what is held minus what is owed (a card's bill, a loan); say so, so the sum can be checked.
        if (finance.owedBalance > 0) {
            Text(
                "${finance.money.format(finance.heldBalance)} in accounts, minus ${finance.money.format(finance.owedBalance)} owed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.md),
            )
        }
        // The same accounts three ways; the choice is the Wallet's own (Settings > Wallet & Cards has it too).
        SectionSubheader("Accounts")
        ConnectedChoiceRow(
            options = accountsLayoutOptions,
            selected = layout,
            onSelect = {
                haptics.selection()
                onLayout(it)
            },
        )
        Spacer(Modifier.height(AppSpacing.md))
        val footer = if (active.isEmpty()) null else "Balances are worked out from the starting balance and every transaction recorded."
        if (layout == AccountsLayout.Rows) {
            ListSection(footer = footer) {
                for (a in active) item { AccountTile(a, cardsPerBank) }
                item { AddAccountRow(actions::addAccount) }
            }
        } else {
            // The place for the next account is a card-shaped tile at the end of them, not a row under them.
            if (active.isNotEmpty()) {
                AccountCards(active, layout, cardsPerBank, bankIconStyle, onAdd = actions::addAccount)
            } else {
                AddTile(
                    title = "Add your first account",
                    supporting = "Cash, a bank, a card or a wallet",
                    onClick = actions::addAccount,
                )
            }
            if (footer != null) {
                Text(
                    footer,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = AppSpacing.lg, end = AppSpacing.lg, top = AppSpacing.md),
                )
            }
        }
        if (archived.isNotEmpty()) {
            TextButton(onClick = onToggleArchived) {
                Icon(if (showArchived) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    if (showArchived) {
                        "Hide archived accounts"
                    } else {
                        "Show ${archived.size} archived ${if (archived.size == 1) "account" else "accounts"}"
                    },
                )
            }
            if (showArchived) {
                if (layout == AccountsLayout.Rows) {
                    ListSection { for (a in archived) item { AccountTile(a, cardsPerBank) } }
                } else {
                    AccountCards(archived, layout, cardsPerBank, bankIconStyle)
                }
            }
        }
    }
}

@Composable
private fun AddAccountRow(onClick: () -> Unit) {
    ListRow(
        headline = "Add account",
        leading = { LeadingIcon(Icons.Rounded.Add) },
        onClick = onClick,
    )
}

/** What is said under an account's amount when the amount needs saying in words, or null when it doesn't. */
private fun accountStanding(balance: Long, kind: AccountKind): String? = when {
    balance < 0 -> if (kind == AccountKind.Card || kind == AccountKind.Other) "Owed" else "Overdrawn"
    // A card paid past its bill: the bank owes the difference back, and it counts as money held.
    balance > 0 && kind == AccountKind.Card -> "In credit"
    else -> null
}

/** The accounts as cards: a stack with the first in front, or a list of them one under another. */
@Composable
private fun AccountCards(
    accounts: List<Account>,
    layout: AccountsLayout,
    cardsPerBank: Map<String, Int>,
    bankIconStyle: BankIconStyle,
    /** When given, a tile to add one more ends the stack or the list. */
    onAdd: (() -> Unit)? = null,
) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val cards = accounts.map { account ->
        val balance = finance.balanceOf(account.id!!)
        val room = creditRoom(account.creditLimit, balance)
        AccountCardData(
            id = account.id,
            name = account.name,
            kind = account.kind,
            bankName = finance.catalog.byKey(account.bankKey)?.name,
            balance = finance.money.format(balance),
            standing = accountStanding(balance, account.kind),
            isDefault = finance.defaultAccount?.id == account.id,
            archived = account.archived,
            cards = account.bankKey?.let { cardsPerBank[it] } ?: 0,
            credit = room?.let { if (it.overLimit) "Over limit" else "${finance.money.format(it.available, trimZeroFraction = true)} left" },
            creditSpoken = room?.let {
                "credit limit ${finance.money.format(it.limit, trimZeroFraction = true)}, " +
                    if (it.overLimit) "over it by ${finance.money.format(-it.available)}" else "${finance.money.format(it.available)} available"
            },
            overLimit = room?.overLimit == true,
            creditUsed = room?.used?.toFloat(),
        )
    }
    fun accountOf(card: AccountCardData) = accounts.first { it.id == card.id }
    if (layout == AccountsLayout.Stack) {
        Column {
            AccountStack(
                accounts = cards,
                onOpen = { navigator.openAccountHistory(it.id) },
                onManage = { actions.manageAccount(accountOf(it)) },
                bankIconStyle = bankIconStyle,
            )
            // The stack's cards are a hair of colour behind the front one: the add tile under it is a slim one.
            if (onAdd != null) {
                Spacer(Modifier.height(AppSpacing.lg))
                AddTile(title = "Add account", supporting = "Cash, a bank, a card or a wallet", onClick = onAdd, compact = true)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
            cards.forEachIndexed { index, card ->
                key(card.id) {
                    // Card art keeps a card's proportions; cap it on wide windows, as the saved cards are.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 440.dp)) {
                            AnimatedEntrance(delayMs = 40L * index) {
                                AccountCardView(
                                    data = card,
                                    onOpen = { navigator.openAccountHistory(card.id) },
                                    onManage = { actions.manageAccount(accountOf(card)) },
                                    bankIconStyle = bankIconStyle,
                                )
                            }
                        }
                    }
                }
            }
            // The next account is a card too: one more of them, drawn empty, where it would go.
            if (onAdd != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.widthIn(max = 440.dp)) {
                        AddTile(title = "Add account", supporting = "Cash, a bank, a card or a wallet", onClick = onAdd)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountTile(account: Account, cardsPerBank: Map<String, Int>) {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val navigator = LocalNavigator.current
    val balance = finance.balanceOf(account.id!!)
    val isDefault = finance.defaultAccount?.id == account.id
    val bank = finance.catalog.byKey(account.bankKey)
    val cards = account.bankKey?.let { cardsPerBank[it] } ?: 0
    val standing = accountStanding(balance, account.kind)
    val room = creditRoom(account.creditLimit, balance)
    val details = listOfNotNull(
        bank?.name,
        account.kind.label,
        room?.let {
            if (it.overLimit) "Over its ${finance.money.format(it.limit, trimZeroFraction = true)} limit"
            else "${finance.money.format(it.available, trimZeroFraction = true)} left of ${finance.money.format(it.limit, trimZeroFraction = true)}"
        },
        if (cards > 0) "$cards ${if (cards == 1) "card" else "cards"}" else null,
        if (isDefault) "Default" else null,
        if (account.archived) "Archived" else null,
    ).joinToString(" · ")
    ListRow(
        headline = account.name,
        supporting = details,
        leading = { BankBadge(bank, account.kind.icon) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    finance.money.format(balance),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = if (balance < 0) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                )
                if (standing != null) {
                    Text(
                        standing,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (balance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        onClick = { navigator.openAccountHistory(account.id) },
        onLongClick = { actions.manageAccount(account) },
    )
}

/** "All" plus one chip per bank, once the wallet holds cards from more than one bank. [selected] is a bank's key. */
@Composable
private fun BankFilter(banks: List<Bank>, selected: String?, onSelected: (String?) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(start = AppSpacing.lg, end = AppSpacing.lg, bottom = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
    ) {
        FilterChip(selected = selected == null, onClick = { onSelected(null) }, label = { Text("All") })
        for (bank in banks) {
            FilterChip(
                selected = selected == bank.key,
                onClick = { onSelected(bank.key) },
                label = { Text(bank.name) },
                leadingIcon = { BankGlyph(bank, tint = MaterialTheme.colorScheme.onSurfaceVariant, size = 18.dp) },
            )
        }
    }
}
