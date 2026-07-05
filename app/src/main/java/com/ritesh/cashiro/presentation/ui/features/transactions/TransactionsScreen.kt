package com.ritesh.cashiro.presentation.ui.features.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ritesh.cashiro.R
import com.ritesh.cashiro.presentation.common.TimePeriod
import com.ritesh.cashiro.presentation.effects.BlurredAnimatedVisibility
import com.ritesh.cashiro.presentation.effects.overScrollVertical
import com.ritesh.cashiro.presentation.effects.rememberOverscrollFlingBehavior
import com.ritesh.cashiro.presentation.ui.components.CashiroCard
import com.ritesh.cashiro.presentation.ui.components.CurrencySelectionBottomSheet
import com.ritesh.cashiro.presentation.ui.components.CustomTitleTopAppBar
import com.ritesh.cashiro.presentation.ui.components.DateRangePickerDialog
import com.ritesh.cashiro.presentation.ui.components.DeleteMultipleTransactionsDialog
import com.ritesh.cashiro.presentation.ui.components.ListItemPosition
import com.ritesh.cashiro.presentation.ui.components.LoadingCircle
import com.ritesh.cashiro.presentation.ui.components.SearchBarBox
import com.ritesh.cashiro.presentation.ui.components.SectionHeader
import com.ritesh.cashiro.presentation.ui.components.TransactionItem
import com.ritesh.cashiro.presentation.ui.components.TransactionTotalsCard
import com.ritesh.cashiro.presentation.ui.components.toShape
import com.ritesh.cashiro.presentation.ui.features.categories.NavigationContent
import com.ritesh.cashiro.presentation.ui.icons.Bag
import com.ritesh.cashiro.presentation.ui.icons.CloseCircle
import com.ritesh.cashiro.presentation.ui.icons.Folder2
import com.ritesh.cashiro.presentation.ui.icons.Iconax
import com.ritesh.cashiro.presentation.ui.icons.Menu
import com.ritesh.cashiro.presentation.ui.icons.ReceiptItem
import com.ritesh.cashiro.presentation.ui.icons.Search
import com.ritesh.cashiro.presentation.ui.theme.Dimensions
import com.ritesh.cashiro.presentation.ui.theme.Spacing
import com.ritesh.cashiro.utils.DateRangeUtils
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class
)
@Composable
fun SharedTransitionScope.TransactionsScreen(
    initialCategory: String? = null,
    initialMerchant: String? = null,
    initialPeriod: String? = null,
    initialCurrency: String? = null,
    initialType: String? = null,
    focusSearch: Boolean = false,
    transactionsViewModel: TransactionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onTransactionClick: (Long, String) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    animatedContentScope: AnimatedVisibilityScope? = null,
    blurEffects: Boolean
) {
    val context = LocalContext.current
    val uiState by transactionsViewModel.uiState.collectAsState()
    val searchQuery by transactionsViewModel.searchQuery.collectAsState()
    val selectedPeriod by transactionsViewModel.selectedPeriod.collectAsState()
    val categoryFilter by transactionsViewModel.categoryFilter.collectAsState()
    val transactionTypeFilter by transactionsViewModel.transactionTypeFilter.collectAsState()
    val deletedTransaction by transactionsViewModel.deletedTransaction.collectAsState()
    val categoriesMap by transactionsViewModel.categories.collectAsState()
    val subcategoriesMap by transactionsViewModel.subcategories.collectAsState()
    val accountsMap by transactionsViewModel.accountsMap.collectAsState()
    val filteredTotals by transactionsViewModel.filteredTotals.collectAsState()
    val currencyGroupedTotals by transactionsViewModel.currencyGroupedTotals.collectAsState()
    val availableCurrencies by transactionsViewModel.availableCurrencies.collectAsState()
    val selectedCurrency by transactionsViewModel.selectedCurrency.collectAsState()
    val baseCurrency by transactionsViewModel.baseCurrency.collectAsState()
    val sortOption by transactionsViewModel.sortOption.collectAsState()
    val smsScanMonths by transactionsViewModel.smsScanMonths.collectAsState()
    val customDateRange by transactionsViewModel.customDateRange.collectAsState()
    val selectionMode by transactionsViewModel.selectionMode.collectAsState()
    val selectedTransactionIds by transactionsViewModel.selectedTransactionIds.collectAsState()
    val deletedTransactions by transactionsViewModel.deletedTransactions.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMainMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    var showCurrencySheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }

    
    // Focus management for search field
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scrollBehaviorSmall = TopAppBarDefaults.pinnedScrollBehavior()
    val scrollBehaviorLarge = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var searchTextFieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        )
    }

    // Sync with external updates (like initial filters)
    LaunchedEffect(searchQuery) {
        if (searchQuery != searchTextFieldValue.text) {
            searchTextFieldValue = searchTextFieldValue.copy(
                text = searchQuery,
                selection = TextRange(searchQuery.length)
            )
        }
    }
    
    // Calculate active filter count for filters
    val activeFilterCount = listOf(
        transactionTypeFilter.isNotEmpty(),
        categoryFilter.isNotEmpty()
    ).count { it }

    // Remember scroll position across navigation
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 48.dp.roundToPx() }
    val isCollapsed by remember(collapseThresholdPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                    listState.firstVisibleItemScrollOffset > collapseThresholdPx
        }
    }

    // Cache expensive operations
    val timePeriods = remember { TimePeriod.entries }
    val customRangeLabel = remember(customDateRange) {
        DateRangeUtils.formatDateRange(customDateRange)
    }
    
    // Apply initial filters only once when screen is first created
    LaunchedEffect(Unit) {
        transactionsViewModel.applyInitialFilters(
            initialCategory,
            initialMerchant,
            initialPeriod,
            initialCurrency,
            initialType
        )
        // Delay heavy data loading until transition finished for smoothness
        delay(500)
        transactionsViewModel.startLoading()
    }

    // Apply navigation filters when navigation parameters change (for deep links)
    LaunchedEffect(initialCategory, initialMerchant, initialPeriod, initialCurrency, initialType) {
        if (initialCategory != null || initialMerchant != null || initialPeriod != null || initialCurrency != null || initialType != null) {
            transactionsViewModel.applyNavigationFilters(
                initialCategory,
                initialMerchant,
                initialPeriod,
                initialCurrency,
                initialType
            )
        }
    }
    
    // Handle delete undo snackbar
    LaunchedEffect(deletedTransaction) {
        deletedTransaction?.let { transaction ->
            // Clear the state immediately to prevent re-triggering
            transactionsViewModel.clearDeletedTransaction()
            
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.transaction_deleted),
                    actionLabel = context.getString(R.string.undo),
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // Pass the transaction directly since state is already cleared
                    transactionsViewModel.undoDeleteTransaction(transaction)
                }
            }
        }
    }
    
    // Handle bulk delete undo snackbar
    LaunchedEffect(deletedTransactions) {
        deletedTransactions?.let { transactions ->
            // Clear the state immediately to prevent re-triggering
            transactionsViewModel.clearDeletedTransactions()
            
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.transactions_deleted_format, transactions.size),
                    actionLabel = context.getString(R.string.undo),
                    duration = SnackbarDuration.Short
                )
                if (result == SnackbarResult.ActionPerformed) {
                    transactionsViewModel.undoDeleteTransactions(transactions)
                }
            }
        }
    }
    
    // Focus search field if requested
    LaunchedEffect(focusSearch) {
        if (focusSearch) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    // Clear snackbar when navigating away
    DisposableEffect(Unit) {
        onDispose {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }
    
    // Handle back button in selection mode
    BackHandler(enabled = selectionMode) {
        transactionsViewModel.toggleSelectionMode()
    }
    
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehaviorLarge.nestedScrollConnection)
            .then(
                if (animatedContentScope != null && (initialCategory != null || initialMerchant != null)) {
                    Modifier.sharedBounds(
                        rememberSharedContentState(
                            key = if (initialCategory != null) {
                                "category_$initialCategory"
                            } else {
                                "merchant_$initialMerchant"
                            }
                        ),
                        animatedVisibilityScope = animatedContentScope,
                        boundsTransform = { _, _ ->
                            spring(
                                stiffness = Spring.StiffnessLow,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        },
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.Fit, Alignment.Center)
                    )
                } else Modifier
            ),
        topBar = {
            CustomTitleTopAppBar(
                title = if (selectionMode) stringResource(R.string.items_selected_format, selectedTransactionIds.size) else stringResource(R.string.transactions),
                scrollBehaviorSmall = scrollBehaviorSmall,
                scrollBehaviorLarge = scrollBehaviorLarge,
                hazeState = hazeState,
                hasBackButton = selectionMode,
                navigationContent = {
                    if (selectionMode) {
                        NavigationContent {
                            transactionsViewModel.toggleSelectionMode()
                        }
                    }
                },
                actionContent = {
                    BlurredAnimatedVisibility(selectionMode) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            // Select All/Deselect All toggle button
                            val allSelected =
                                selectedTransactionIds.size == uiState.transactions.size && uiState.transactions.isNotEmpty()
                            IconButton(
                                onClick = {
                                    if (allSelected) {
                                        transactionsViewModel.clearSelection()
                                    } else {
                                        transactionsViewModel.selectAllTransactions()
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                shapes =  IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = if (allSelected) Icons.Rounded.Deselect else Icons.Rounded.SelectAll,
                                    contentDescription = if (allSelected) stringResource(R.string.deselect_all) else stringResource(R.string.select_all),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            // Delete button
                            IconButton(
                                onClick = { showDeleteConfirmation = true },
                                enabled = selectedTransactionIds.isNotEmpty(),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    contentColor = if (selectedTransactionIds.isNotEmpty())
                                        MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                                ),
                                shapes =  IconButtonDefaults.shapes(),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Iconax.Bag,
                                    contentDescription = stringResource(R.string.delete_selected),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = {
                    Snackbar(
                        snackbarData = it,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.large,
                    )
                }
            ) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            // Search Bar with Sort Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimensions.Padding.content)
                    .padding(top = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SearchBarBox(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(searchFocusRequester)
                        .then(Modifier),
                    searchQuery = searchTextFieldValue,
                    onSearchQueryChange = {
                        searchTextFieldValue = it
                        transactionsViewModel.updateSearchQuery(it.text)
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Iconax.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    },
                    trailingIcon = {
                        Row{
                            BlurredAnimatedVisibility(searchTextFieldValue.text.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchTextFieldValue = TextFieldValue("")
                                    transactionsViewModel.updateSearchQuery("")
                                }) {
                                    Icon(
                                        imageVector = Iconax.CloseCircle,
                                        contentDescription = stringResource(R.string.clear_search),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                }
                            }
                            // More options button
                            Box {
                                IconButton(
                                    onClick = { showMainMenu = true },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(MaterialTheme.shapes.medium)
                                        .background(Color.Transparent)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreHoriz,
                                        contentDescription = stringResource(R.string.more_options),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showMainMenu,
                                    onDismissRequest = { showMainMenu = false },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.filters)) },
                                        onClick = { showFilterSheet = true; showMainMenu = false },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Iconax.Folder2,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                    HorizontalDivider(
                                        thickness = 1.5.dp,
                                        color = MaterialTheme.colorScheme.surface
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sort)) },
                                        onClick = { showSortMenu = true; showMainMenu = false },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Iconax.Menu,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    SortOption.entries.forEachIndexed { index, option ->
                                        val isFirstItem = index == 0
                                        val isLastItem = index == SortOption.entries.lastIndex
                                        val isMiddleItem = !isFirstItem && !isLastItem
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        Spacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = sortOption == option,
                                                        onClick = null,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Text(option.label)
                                                }
                                            },
                                            onClick = {
                                                transactionsViewModel.setSortOption(option)
                                                showSortMenu = false
                                            }
                                        )
                                        // Add a Spacer for middle items
                                        if (isMiddleItem || (isFirstItem && SortOption.entries.size > 2) ) {
                                            HorizontalDivider(
                                                thickness = 1.5.dp,
                                                color = MaterialTheme.colorScheme.surface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    label = {
                        Text(
                            text = if (categoryFilter.isNotEmpty()) {
                                stringResource(R.string.search_in_categories_format, categoryFilter.joinToString(", "))
                            } else stringResource(R.string.search_transactions),
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f)
                        )
                    }
                )
            }

            // Period Filter Chips - hide on scroll down
            AnimatedVisibility(
                visible = !isCollapsed,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Column {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.sm),
                        contentPadding = PaddingValues(horizontal = Dimensions.Padding.content),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        // Period filter chips
                        items(timePeriods) { period ->
                            FilterChip(
                                // Only show CUSTOM as selected if both period is CUSTOM AND dates are set
                                selected = if (period == TimePeriod.CUSTOM) {
                                    selectedPeriod == period && customDateRange != null
                                } else {
                                    selectedPeriod == period
                                },
                                onClick = {
                                    if (period == TimePeriod.CUSTOM) {
                                        showDateRangePicker = true
                                        // Don't change selectedPeriod until user confirms dates
                                    } else {
                                        transactionsViewModel.selectPeriod(period)
                                    }
                                },
                                label = {
                                    Text(
                                        if (period == TimePeriod.CUSTOM && customRangeLabel != null) {
                                            customRangeLabel
                                        } else {
                                            period.label
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(0.7f),
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    borderWidth = 0.dp,
                                    selected = if (period == TimePeriod.CUSTOM) {
                                        selectedPeriod == period && customDateRange != null
                                    } else {
                                        selectedPeriod == period
                                    },
                                    enabled = true
                                ),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                }
            }

            // Transaction List
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Dimensions.Padding.content),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingCircle()
                    }
                }
                uiState.transactions.isEmpty() -> {
                    EmptyTransactionsState(
                        searchQuery = searchQuery,
                        selectedPeriod = selectedPeriod
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = Dimensions.Padding.content)
                            .padding(top = Spacing.sm)
                            .clip(RoundedCornerShape(20.dp))
                            .overScrollVertical(),
                        flingBehavior = rememberOverscrollFlingBehavior { listState },
                        contentPadding = PaddingValues(
                            bottom = 150.dp
                        )

                    ) {
                        stickyHeader {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                TransactionTotalsCard(
                                    income = filteredTotals.income,
                                    expenses = filteredTotals.expenses,
                                    netBalance = filteredTotals.netBalance,
                                    currency = baseCurrency,
                                    isLoading = uiState.isLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = Spacing.sm)
                                )
                            }
                        }

                        // Iterate through date groups in order
                        listOf(
                            DateGroup.TODAY,
                            DateGroup.YESTERDAY,
                            DateGroup.THIS_WEEK,
                            DateGroup.EARLIER
                        ).forEach { dateGroup ->
                            uiState.groupedTransactions[dateGroup]?.let { transactions ->
                                // Date group header
                                stickyHeader {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                        modifier = Modifier
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.surface,
                                                        MaterialTheme.colorScheme.surface,
                                                        MaterialTheme.colorScheme.surface,
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                            .padding(
                                                top = Spacing.md,
                                                bottom = Spacing.md,
                                                start = Spacing.sm
                                            )

                                    ) {
                                        Spacer(
                                            modifier =
                                                Modifier
                                                    .height(16.dp)
                                                    .width(4.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.tertiary,
                                                        RoundedCornerShape(14.dp)
                                                    )

                                        )
                                        SectionHeader(title = dateGroup.label)
                                    }
                                }

                                // Transactions in this group
                                itemsIndexed(
                                    items = transactions,
                                    key = { _, it -> it.id }
                                ) { index, transaction ->
                                    val position = ListItemPosition.from(index, transactions.size)
                                    TransactionItem(
                                        transaction = transaction,
                                        categoryEntity = categoriesMap[transaction.category],
                                        subcategoryEntity = transaction.subcategory?.let { subcategoriesMap[it] },
                                        accountIconResId = accountsMap["${transaction.bankName}_${transaction.accountNumber}"]?.iconResId ?: 0,
                                        accountIconName = accountsMap["${transaction.bankName}_${transaction.accountNumber}"]?.iconName,
                                        accountColorHex = accountsMap["${transaction.bankName}_${transaction.accountNumber}"]?.color,
                                        showDate = dateGroup == DateGroup.EARLIER,
                                        shape = position.toShape(),
                                        onClick = { onTransactionClick(transaction.id, "transaction_${transaction.id}") },
                                        animatedContentScope = animatedContentScope,
                                        sharedElementKey = "transaction_${transaction.id}",
                                        isSelectionMode = selectionMode,
                                        isSelected = selectedTransactionIds.contains(transaction.id),
                                        onSelectionToggle = { transactionsViewModel.toggleTransactionSelection(transaction.id) },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                transactionsViewModel.toggleSelectionMode()
                                                transactionsViewModel.toggleTransactionSelection(transaction.id)
                                            }
                                        },
                                        convertedAmount = uiState.convertedAmounts[transaction.id],
                                        mainCurrency = baseCurrency
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Export Dialog

    if (showDateRangePicker) {
        DateRangePickerDialog(
            onDismiss = { showDateRangePicker = false },
            onConfirm = { startDate, endDate ->
                transactionsViewModel.setCustomDateRange(startDate, endDate)
                showDateRangePicker = false
            },
            initialStartDate = customDateRange?.first,
            initialEndDate = customDateRange?.second,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showCurrencySheet) {
        CurrencySelectionBottomSheet(
            selectedCurrency = selectedCurrency,
            availableCurrencies = availableCurrencies,
            onCurrencySelected = {
                transactionsViewModel.selectCurrency(it)
                showCurrencySheet = false
            },
            onDismiss = { showCurrencySheet = false }
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        DeleteMultipleTransactionsDialog(
            onDelete = {
                transactionsViewModel.deleteSelectedTransactions()
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false },
            selectedTransactionIds = selectedTransactionIds,
            blurEffects = blurEffects,
            hazeState = hazeState
        )
    }

    if (showFilterSheet) {
        TransactionsFilterBottomSheet(
            viewModel = transactionsViewModel,
            onDismiss = { showFilterSheet = false }
        )
    }
}




@Composable
private fun EmptyTransactionsState(
    searchQuery: String,
    selectedPeriod: TimePeriod
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimensions.Padding.content),
        contentAlignment = Alignment.Center
    ) {
        CashiroCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimensions.Padding.content),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Iconax.ReceiptItem,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = when {
                        searchQuery.isNotEmpty() -> stringResource(R.string.no_transactions_matching_format, searchQuery)
                        selectedPeriod != TimePeriod.ALL -> stringResource(R.string.no_transactions_for_format, selectedPeriod.label.lowercase())
                        else -> stringResource(R.string.no_transactions_yet)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (searchQuery.isEmpty() && selectedPeriod == TimePeriod.ALL) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = stringResource(R.string.sync_sms_to_see_transactions),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
