package com.example.normpix.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.Bank
import com.example.normpix.banks.ui.BankBadge
import com.example.normpix.banks.ui.BankFormSheet
import com.example.normpix.banks.ui.BankRenameSheet
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.ui.LocalFinance
import com.example.normpix.finance.ui.rememberFinanceState
import com.example.normpix.navigation.LocalAppContainer
import com.example.normpix.navigation.LocalNavigator
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppSubPage
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.LocalOverlays
import com.example.normpix.ui.components.LocalSnackbars
import com.example.normpix.ui.components.SearchField
import com.example.normpix.ui.components.SectionSubheader
import com.example.normpix.ui.components.SnackbarKind
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.components.rememberBottomControls
import com.example.normpix.ui.theme.AppLayout
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.wallet.BankCategory
import com.example.normpix.wallet.CardsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** The list item holding the search field, found by this key to tell whether the field is on screen. */
private const val SEARCH_ITEM_KEY = "search"

/** How many of the user's accounts and saved cards belong to one bank. */
internal data class BankUsage(val accounts: Int, val cards: Int) {
    val any: Boolean get() = accounts > 0 || cards > 0

    /** "2 accounts · 1 card", or null when nothing uses the bank. */
    val words: String?
        get() = listOfNotNull(
            accounts.takeIf { it > 0 }?.let { "$it ${if (it == 1) "account" else "accounts"}" },
            cards.takeIf { it > 0 }?.let { "$it ${if (it == 1) "card" else "cards"}" },
        ).joinToString(" · ").ifEmpty { null }
}

/** What the page's chips filter by: everything, only the banks in use, the user's own, or one kind of bank. */
internal sealed interface BankFilter {
    val key: String

    data object All : BankFilter { override val key = "all" }

    data object InUse : BankFilter { override val key = "used" }

    data object Yours : BankFilter { override val key = "yours" }

    data class Kind(val category: BankCategory) : BankFilter { override val key = category.name }

    companion object {
        fun of(key: String): BankFilter = when (key) {
            All.key -> All
            InUse.key -> InUse
            Yours.key -> Yours
            else -> BankCategory.entries.firstOrNull { it.name == key }?.let(::Kind) ?: All
        }
    }
}

/** One group of banks on the page: a heading and the banks under it. */
internal class BankGroup(val key: String, val title: String, val banks: List<Bank>)

/**
 * The banks as the Banks page shows them, for what the user has typed and the chip they chose: the user's own banks first, then
 * each kind of bank in the directory's order, a group left out when nothing in it matches. Typing finds a bank by its name or
 * by any other name it goes by ([com.example.normpix.banks.BankCatalog.matching]).
 */
internal fun bankGroups(matching: List<Bank>, filter: BankFilter, used: Map<String, BankUsage>): List<BankGroup> {
    val shown = matching.filter {
        when (filter) {
            BankFilter.All -> true
            BankFilter.InUse -> used[it.key]?.any == true
            BankFilter.Yours -> !it.builtIn
            is BankFilter.Kind -> it.category == filter.category
        }
    }
    val yours = shown.filter { !it.builtIn }
    return buildList {
        if (yours.isNotEmpty()) add(BankGroup("yours", "Added by you", yours))
        for (category in BankCategory.entries) {
            val inGroup = shown.filter { it.category == category }
            if (inGroup.isNotEmpty()) add(BankGroup(category.name, category.label, inGroup))
        }
    }
}

/** Settings > Banks & Categories, opened on Banks: the legacy page name kept as a shell. */
@Composable
fun BanksSettingsPage() = BanksAndCategoriesPage(BanksAndCategoriesTab.Banks)

/**
 * The Banks side of Settings > Banks & Categories: every bank NormPix knows, each with its logo - the built-in directory of
 * India's banks (public sector, private, small finance, payments, digital & neobanks, foreign, co-operative, regional rural,
 * and the earlier names of banks that merged) and the banks you added. A search finds one by any name it goes by, the chips
 * narrow the list to a kind of bank (or to those in use), and a bank that isn't there can be added from here, to be used by
 * accounts and cards like any other.
 *
 * [tabBar] is the page's own switch between its two sides, at the top of the list; the page around it is [BanksAndCategoriesPage].
 */
@Composable
fun BanksContent(title: String, tabBar: @Composable () -> Unit) {
    val navigator = LocalNavigator.current
    val finance = rememberFinanceState()
    val container = LocalAppContainer.current
    val cardsState by container.cards.state.collectAsState()
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val controller = LocalFinance.current
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    val catalog = finance.catalog
    var search by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf(BankFilter.All.key) }
    val filter = BankFilter.of(chosen)

    val used = remember(finance.accounts, cardsState) {
        val accounts = finance.accounts.mapNotNull { it.bankKey }.groupingBy { it }.eachCount()
        val cards = (cardsState as? CardsState.Ready)?.cards.orEmpty().mapNotNull { it.bankKey }.groupingBy { it }.eachCount()
        (accounts.keys + cards.keys).associateWith { BankUsage(accounts[it] ?: 0, cards[it] ?: 0) }
    }
    val query = search.trim()
    val groups = remember(catalog, query, filter, used) { bankGroups(catalog.matching(query), filter, used) }

    fun renameBank(bank: Bank) {
        scope.launch {
            val renamed = overlays.sheet<Boolean> { dismiss -> BankRenameSheet(bank, onDone = dismiss) }
            if (renamed == true) snackbars.show("Bank renamed")
        }
    }

    fun deleteBank(bank: Bank) {
        scope.launch {
            val confirmed = overlays.confirm(
                title = "Delete ${bank.name}?",
                message = "It is removed from your list of banks. No account or card uses it, so nothing else changes. You can add it again later.",
                confirmLabel = "Delete",
                destructive = true,
                icon = Icons.Outlined.DeleteOutline,
            )
            if (!confirmed) return@launch
            try {
                controller.deleteBank(bank.key)
                haptics.success()
                snackbars.show("${bank.name} deleted", SnackbarKind.Deleted)
            } catch (e: FinanceValidationException) {
                snackbars.show(e.message ?: "Couldn't delete the bank.", SnackbarKind.Error)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                snackbars.show("Couldn't delete the bank. Nothing was changed.", SnackbarKind.Error)
            }
        }
    }

    fun showBank(bank: Bank) {
        haptics.selection()
        scope.launch {
            // Rename and delete are asked for in the sheet and done here once it has closed, so nothing is said from inside it.
            when (overlays.sheet<BankAction> { dismiss -> BankDetailSheet(bank, used[bank.key], onAction = dismiss) }) {
                BankAction.Rename -> renameBank(bank)
                BankAction.Delete -> deleteBank(bank)
                BankAction.Done, null -> Unit
            }
        }
    }

    fun addBank() {
        scope.launch { overlays.sheet<String> { dismiss -> BankFormSheet(initialName = query, onDone = dismiss) } }
    }

    // With Bottom controls on, the search sits in a card at the foot of the page for good: nothing to scroll away from.
    val searchAtBottom = rememberBottomControls()
    // Once the search field has scrolled out of sight it waits in the header as an icon beside "+", as on the Settings screen:
    // tapping it brings the field back and puts the cursor in it. "Out of sight" is the field's own item being wholly above the
    // list (the summary item above it moving off is not enough).
    val listState = rememberLazyListState()
    val searchFocus = remember { FocusRequester() }
    val searchOutOfView by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            // Not measured yet: nothing has scrolled anywhere.
            if (layout.visibleItemsInfo.isEmpty()) return@derivedStateOf false
            val field = layout.visibleItemsInfo.firstOrNull { it.key == SEARCH_ITEM_KEY } ?: return@derivedStateOf true
            field.offset + field.size <= 0
        }
    }
    AppSubPage(
        title = title,
        onBack = navigator::back,
        maxWidth = AppLayout.maxListWidth,
        listState = listState,
        actions = {
            AnimatedVisibility(visible = searchOutOfView && !searchAtBottom) {
                IconButton(
                    onClick = {
                        scope.launch {
                            listState.animateScrollToItem(0)
                            runCatching { searchFocus.requestFocus() }
                        }
                    },
                ) { Icon(Icons.Rounded.Search, contentDescription = "Search banks") }
            }
            IconButton(onClick = ::addBank) { Icon(Icons.Rounded.Add, contentDescription = "Add a bank") }
        },
        bottomSearch = if (searchAtBottom) {
            { SearchField(value = search, onValueChange = { search = it }, placeholder = "Search banks") }
        } else {
            null
        },
    ) {
        item(key = "tabs") { tabBar() }
        item(key = "summary") {
            val total = catalog.all.size
            val inUse = catalog.all.count { used[it.key]?.any == true }
            Column(Modifier.padding(AppSpacing.page)) {
                Text(
                    "$total banks" + if (inUse > 0) " · $inUse in use" else "",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    "Search by name, or by what a bank is also called (SBI, HDFC). Banks you add show here too.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!searchAtBottom) {
            item(key = SEARCH_ITEM_KEY) {
                Column(Modifier.padding(AppSpacing.page)) {
                    SearchField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "Search banks",
                        modifier = Modifier.focusRequester(searchFocus),
                    )
                }
            }
        }
        item(key = "filters") {
            val hasYours = catalog.all.any { !it.builtIn }
            val options = buildList {
                add(BankFilter.All to "All")
                if (used.isNotEmpty()) add(BankFilter.InUse to "In use")
                if (hasYours) add(BankFilter.Yours to "Added by you")
                for (category in BankCategory.entries) {
                    val label = when (category) {
                        BankCategory.Digital -> "Digital & apps"
                        else -> category.label.removeSuffix(" banks").removeSuffix(" bank")
                    }
                    add(BankFilter.Kind(category) to label)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                for ((option, label) in options) {
                    val selected = option == filter
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.selection()
                            chosen = option.key
                        },
                        label = { Text(label) },
                        shape = CircleShape,
                        // One choice of several, so it is announced as a radio button rather than the checkbox a filter chip is.
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
        if (groups.isEmpty()) {
            item(key = "none") {
                AppEmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = if (query.isNotEmpty()) "No bank called \"$query\"" else "No banks here yet",
                    message = if (filter == BankFilter.InUse) {
                        "Banks you give to an account or a card show here."
                    } else {
                        "Check the spelling, or add it: it is then in your lists like any other bank."
                    },
                    actionLabel = if (query.isNotEmpty()) "Add \"$query\"" else "Add a bank",
                    actionIcon = Icons.Rounded.Add,
                    onAction = ::addBank,
                )
            }
        }
        for (group in groups) {
            item(key = "group-${group.key}") {
                Column(Modifier.padding(horizontal = AppSpacing.lg)) {
                    SectionSubheader("${group.title} · ${group.banks.size}")
                    ListSection {
                        for (bank in group.banks) {
                            item {
                                ListRow(
                                    headline = bank.name,
                                    supporting = bankLine(bank, used[bank.key]),
                                    leading = { BankBadge(bank, Icons.Outlined.AccountBalance) },
                                    trailing = used[bank.key]?.takeIf { it.any }?.let { { InUsePill() } },
                                    onClick = { showBank(bank) },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (groups.isNotEmpty()) {
            item(key = "add") {
                Column(Modifier.padding(horizontal = AppSpacing.lg).padding(top = AppSpacing.lg)) {
                    ListSection(footer = "A bank that is missing can be added here, or from the bank field of an account or a card.") {
                        item {
                            ListRow(
                                headline = "Add a bank",
                                supporting = "Missing from the list? Add it by name",
                                leading = { LeadingIcon(Icons.Rounded.Add) },
                                onClick = ::addBank,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The line under a bank's name: what it used to be or merged into, else what else it is called, and what of yours uses it. */
internal fun bankLine(bank: Bank, usage: BankUsage?): String? {
    val identity = bank.note
        ?: if (!bank.builtIn) "Added by you" else bank.aliases.takeIf { it.isNotEmpty() }?.joinToString(", ")
    return listOfNotNull(identity, usage?.words).joinToString(" · ").ifEmpty { null }
}

/** A small mark on a bank in use, in words as well as colour. */
@Composable
private fun InUsePill() {
    val scheme = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer) {
        Text(
            "In use",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        )
    }
}

/** What the bank's sheet asks for: closing it, or - for a bank the user added - renaming or deleting it. */
private enum class BankAction { Done, Rename, Delete }

/**
 * One bank in a sheet: its logo large, what it is, what else it is called, and which of your accounts and cards use it. A bank
 * the user added can also be renamed or deleted; delete is there only while nothing of theirs uses the bank, and says why when not.
 */
@Composable
private fun BankDetailSheet(bank: Bank, usage: BankUsage?, onAction: (BankAction) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankBadge(bank, Icons.Outlined.AccountBalance, size = 64.dp)
            Column(Modifier.padding(start = AppSpacing.lg)) {
                Text(bank.name, style = MaterialTheme.typography.titleLarge, color = scheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    bank.category?.label ?: "Added by you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
        val facts = listOfNotNull(
            bank.note,
            bank.aliases.takeIf { it.isNotEmpty() }?.let { "Also written as ${it.joinToString(", ")}" },
            usage?.words?.let { "Used by $it of yours" } ?: "Not used by any of your accounts or cards yet",
            if (bank.logo == null) "No logo on file, so a plain bank icon stands in" else null,
        )
        for (fact in facts) {
            Text(fact, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(bottom = AppSpacing.xs))
        }
        Spacer(Modifier.height(AppSpacing.lg))
        if (!bank.builtIn) {
            val inUse = usage?.any == true
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), modifier = Modifier.fillMaxWidth()) {
                FilledTonalButton(onClick = { onAction(BankAction.Rename) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("Rename", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { onAction(BankAction.Delete) },
                    enabled = !inUse,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text("Delete", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (inUse) {
                Text(
                    "To delete it, first change the accounts and cards that use it to another bank.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.sm),
                )
            }
            Spacer(Modifier.height(AppSpacing.md))
        }
        Button(onClick = { onAction(BankAction.Done) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Done", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}
