package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.normpix.banks.ui.BankChoice
import com.example.normpix.banks.ui.BankSelector
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.NUMBER_ENDING_LENGTH
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.CategoryType
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.numberEndingOrNull
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.ui.components.AppHaptics
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusColorRole
import com.example.normpix.wallet.CardsState
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.abs

private const val NO_PARENT = 0L

@Composable
fun CategoryFormSheet(
    type: CategoryType,
    existing: Category? = null,
    onDone: (Category) -> Unit,
    parent: Category? = null,
) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: "other") }
    var color by remember { mutableIntStateOf(existing?.color ?: 0) }
    var parentId by remember { mutableStateOf<Long?>(existing?.parentId ?: parent?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (existing == null) {
            runCatching { focus.requestFocus() }
        }
    }

    val hasSubCategories = existing != null && finance.categories.any { it.parentId == existing.id }
    val parents = finance.categories.filter {
        it.type == type &&
            it.parentId == null &&
            it.id != existing?.id &&
            (!it.archived || it.id == parentId)
    }
    val chosenParent = parents.firstOrNull { it.id == parentId }

    val now = LocalDateTime.now()
    val preview = Category(
        id = null,
        name = name,
        type = type,
        icon = icon,
        color = chosenParent?.color ?: color,
        archived = false,
        sortOrder = 0,
        createdAt = now,
        updatedAt = now,
        parentId = null,
    )

    val typeLabel = if (type == CategoryType.Income) "Income" else "Expense"
    val initialSnapshot = remember {
        listOf(name, icon, color, parentId).joinToString("|")
    }
    val hasChanges = listOf(name, icon, color, parentId).joinToString("|") != initialSnapshot

    DiscardChangesGuard(
        dirty = hasChanges,
        isEditing = existing != null,
        keptMessage = "The category stays as it was.",
        saving = saving,
    )

    val title = if (existing == null) {
        if (parent != null) "New sub-category" else "New category"
    } else {
        "Edit category"
    }
    val subtitle = if (chosenParent != null) {
        "$typeLabel category in ${chosenParent.name}"
    } else {
        "$typeLabel category"
    }
    val action = if (existing == null) "Save category" else "Save changes"

    fun save() {
        val draft = if (existing != null) {
            existing.copy(
                name = name,
                icon = icon,
                color = color,
                parentId = parentId,
                updatedAt = LocalDateTime.now(),
            )
        } else {
            Category(
                id = null,
                name = name,
                type = type,
                icon = icon,
                color = color,
                archived = false,
                sortOrder = 0,
                createdAt = LocalDateTime.now(),
                updatedAt = LocalDateTime.now(),
                parentId = parentId,
            )
        }
        saving = true
        error = null
        scope.launch {
            try {
                val saved = controller.saveCategory(draft)
                haptics.success()
                onDone(saved)
            } catch (e: FinanceValidationException) {
                error = e.message
            } catch (e: FinanceConflictException) {
                error = e.message
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = title,
        saveLabel = action,
        onSave = { save() },
        subtitle = subtitle,
        saving = saving,
        titleTrailing = {
            Box(Modifier.clearAndSetSemantics { }) {
                CategoryAvatar(preview)
            }
        },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                error = null
                brandKeyForName(it)?.let { brandKey ->
                    if (existing == null || icon == "other" || isBrandIcon(icon)) {
                        icon = brandKey
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            label = { Text("Name") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            singleLine = true,
            shape = TonalFieldShape,
            colors = typedFieldColors(),
        )

        if (!hasSubCategories && parents.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.lg))
            val options = listOf(
                ChoiceOption(
                    key = NO_PARENT,
                    label = "None (top-level)",
                    icon = Icons.Outlined.Category,
                )
            ) + parents.map { p ->
                ChoiceOption(
                    key = p.id!!,
                    label = p.name,
                    icon = categoryIcon(p.icon),
                    tint = paletteColor(p.color),
                    glyph = {
                        CategoryGlyph(
                            icon = p.icon,
                            tint = paletteColor(p.color).onContainer,
                            size = 22.dp,
                            name = p.name,
                        )
                    },
                )
            }
            ChoiceDropdownField(
                label = "Under",
                options = options,
                selected = parentId ?: NO_PARENT,
                onSelect = { parentId = if (it == NO_PARENT) null else it },
                emptyIcon = Icons.Outlined.Category,
            )
        }

        Spacer(Modifier.height(AppSpacing.xl))
        FormGroupLabel("Icon")
        CategoryIconPicker(icon = icon, onSelect = { icon = it })

        Spacer(Modifier.height(AppSpacing.xl))
        FormGroupLabel("Color")
        if (chosenParent != null) {
            Text(
                text = "Takes the color of ${chosenParent.name}, so they read as one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                for (i in 0 until paletteSize) {
                    val colors = paletteColor(i)
                    val label = paletteNames[i]
                    val selected = color == i
                    ColorDot(
                        colors = colors,
                        label = label,
                        selected = selected,
                        onClick = {
                            haptics.selection()
                            color = i
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun ColorDot(
    colors: PaletteColors,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colors.container)
            .border(
                2.dp,
                if (selected) colors.onContainer else Color.Transparent,
                CircleShape,
            )
            .clickable(onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = label
                role = Role.Button
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onContainer,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

@Composable
fun AccountFormSheet(
    existing: Account? = null,
    onDone: (Account) -> Unit,
) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()

    val opening = existing?.openingBalance ?: 0L
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var amount by remember {
        mutableStateOf(
            if (opening != 0L) minorToInputText(abs(opening), finance.currency.decimals) else "",
        )
    }
    var kind by remember { mutableStateOf(existing?.kind ?: AccountKind.Bank) }
    var bank by remember {
        mutableStateOf(
            BankChoice(
                key = existing?.bankKey?.takeIf { finance.catalog.byKey(it) != null },
            ),
        )
    }
    var bankError by remember { mutableStateOf<String?>(null) }
    var owed by remember { mutableStateOf(opening < 0L) }
    var limit by remember {
        mutableStateOf(
            existing?.creditLimit?.let { minorToInputText(it, finance.currency.decimals) } ?: "",
        )
    }
    var limitError by remember { mutableStateOf<String?>(null) }
    var statementDay by remember { mutableStateOf(existing?.statementDay) }
    var dueDay by remember { mutableStateOf(existing?.dueDay) }
    // What tells a shared receipt which account this is: the number's last digits, and the Wallet card (if any) it is.
    var numberEnding by remember { mutableStateOf(existing?.numberEnding.orEmpty()) }
    var numberError by remember { mutableStateOf<String?>(null) }
    var cardId by remember { mutableStateOf(existing?.cardId) }
    val cardsState = LocalWalletCards.current?.state?.collectAsState()?.value
    val walletCards = (cardsState as? CardsState.Ready)?.cards.orEmpty()
    // A link to a card since deleted shows as none, and is dropped on save - but only once the Wallet has really loaded.
    val shownCardId = cardId?.takeIf { id -> walletCards.any { it.id == id } }
    var makeDefault by remember {
        mutableStateOf(existing != null && finance.defaultAccountId == existing.id)
    }
    var nameError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (existing == null) {
            runCatching { focus.requestFocus() }
        }
    }

    val initialSnapshot = remember {
        listOf(name, amount, kind, bank.key, owed, makeDefault, limit, statementDay, dueDay, numberEnding, cardId).joinToString("|")
    }
    val hasChanges = listOf(name, amount, kind, bank.key, owed, makeDefault, limit, statementDay, dueDay, numberEnding, cardId)
        .joinToString("|") != initialSnapshot

    DiscardChangesGuard(
        dirty = hasChanges,
        isEditing = existing != null,
        keptMessage = "The account stays as it was.",
        saving = saving,
    )

    val scheme = MaterialTheme.colorScheme
    val kinds = remember {
        AccountKind.entries.map { k ->
            ChoiceOption(
                key = k,
                label = k.label,
                icon = k.icon,
            )
        }
    }
    val title = if (existing == null) "New account" else "Edit account"
    val action = if (existing == null) "Save account" else "Save changes"

    fun save() {
        val text = amount.trim()
        val parsed = if (text.isEmpty()) 0L else parseMinorUnits(text, finance.currency.decimals)
        nameError = if (name.isBlank()) "Give the account a name" else null
        amountError = if (parsed == null) "Enter a valid amount" else null
        val hasBank = kind != AccountKind.Cash
        bankError = if (hasBank && bank.unmatched) "Pick a bank from the list, or add it as a new bank" else null
        val limitText = limit.trim()
        val limitMinor = if (kind == AccountKind.Card && limitText.isNotEmpty()) {
            parseMinorUnits(limitText, finance.currency.decimals)
        } else {
            null
        }
        limitError = if (kind == AccountKind.Card && limitText.isNotEmpty()) {
            if (limitMinor == null) "Enter a valid amount"
            else if (limitMinor <= 0L) "Enter a limit above zero"
            else null
        } else {
            null
        }

        val endingText = numberEnding.trim()
        numberError = if (hasBank && endingText.isNotEmpty() && numberEndingOrNull(endingText) == null) {
            "Enter all $NUMBER_ENDING_LENGTH digits, or leave it empty"
        } else {
            null
        }

        if (nameError == null && amountError == null && bankError == null && limitError == null && numberError == null && parsed != null) {
            val now = LocalDateTime.now()
            val openingBalance = if (owed) -parsed else parsed
            val bankKey = if (hasBank) bank.key else null
            val savedEnding = endingText.takeIf { hasBank && it.isNotEmpty() }
            val savedCard = (if (cardsState is CardsState.Ready) shownCardId else cardId).takeIf { hasBank }
            val draft = if (existing != null) {
                existing.copy(
                    name = name,
                    kind = kind,
                    openingBalance = openingBalance,
                    bankKey = bankKey,
                    creditLimit = limitMinor,
                    statementDay = statementDay.takeIf { kind == AccountKind.Card },
                    dueDay = dueDay.takeIf { kind == AccountKind.Card },
                    numberEnding = savedEnding,
                    cardId = savedCard,
                    updatedAt = now,
                )
            } else {
                Account(
                    id = null,
                    name = name,
                    kind = kind,
                    openingBalance = openingBalance,
                    archived = false,
                    sortOrder = finance.accounts.size,
                    createdAt = now,
                    updatedAt = now,
                    bankKey = bankKey,
                    creditLimit = limitMinor,
                    statementDay = statementDay.takeIf { kind == AccountKind.Card },
                    dueDay = dueDay.takeIf { kind == AccountKind.Card },
                    numberEnding = savedEnding,
                    cardId = savedCard,
                )
            }
            saving = true
            scope.launch {
                try {
                    val saved = controller.saveAccount(draft)
                    val wasDefault = finance.defaultAccountId == saved.id
                    if (makeDefault != wasDefault) {
                        controller.setDefaultAccountId(if (makeDefault) saved.id else null)
                    }
                    haptics.success()
                    onDone(saved)
                } catch (e: FinanceValidationException) {
                    nameError = e.message
                } finally {
                    saving = false
                }
            }
        }
    }

    EditorSheet(
        title = title,
        saveLabel = action,
        onSave = { save() },
        subtitle = "Cash, a bank account, a card, or a wallet app",
        saving = saving,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                nameError = null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
            label = { Text("Name") },
            placeholder = { Text("e.g. HDFC Savings") },
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            shape = TonalFieldShape,
            colors = typedFieldColors(),
        )

        Spacer(Modifier.height(AppSpacing.lg))
        ChoiceDropdownField(
            label = "Type",
            options = kinds,
            selected = kind,
            onSelect = { kind = it },
        )

        if (kind != AccountKind.Cash) {
            Spacer(Modifier.height(AppSpacing.lg))
            BankSelector(
                choice = bank,
                onChoice = {
                    bank = it
                    bankError = null
                },
                optional = true,
                errorText = bankError,
            )
            Spacer(Modifier.height(AppSpacing.lg))
            AccountNumberFields(
                kind = kind,
                numberEnding = numberEnding,
                onNumberEnding = {
                    numberEnding = it
                    numberError = null
                },
                numberError = numberError,
                cards = linkableCards(walletCards, finance.accounts, existing),
                cardId = shownCardId,
                onCardId = { cardId = it },
            )
        }

        if (kind == AccountKind.Card) {
            Spacer(Modifier.height(AppSpacing.lg))
            OutlinedTextField(
                value = limit,
                onValueChange = { entered ->
                    limit = entered.filter { it.isDigit() || it == '.' }
                    limitError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Credit limit (optional)") },
                prefix = { Text(finance.currency.symbol) },
                supportingText = {
                    Text(
                        limitError
                            ?: "What the bank lets you borrow on this card. The Wallet then shows how much is left to spend.",
                    )
                },
                isError = limitError != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next,
                ),
                singleLine = true,
                shape = TonalFieldShape,
                colors = typedFieldColors(),
            )
            // The two days the bank sets for the card's month; both come round every month.
            Spacer(Modifier.height(AppSpacing.lg))
            BillingDayField(label = "Statement date (optional)", day = statementDay, onChange = { statementDay = it })
            Spacer(Modifier.height(AppSpacing.lg))
            BillingDayField(label = "Payment due date (optional)", day = dueDay, onChange = { dueDay = it })
            Text(
                text = "The days your bank gives you, repeated every month. The Wallet then counts down to the due date while " +
                    "something is owed, and a reminder can follow it.",
                modifier = Modifier.padding(top = AppSpacing.sm, start = AppSpacing.lg),
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(AppSpacing.xxl))
        val startingMinor = amount.trim().takeIf { it.isNotEmpty() }?.let {
            parseMinorUnits(it, finance.currency.decimals)
        } ?: 0L
        val owedSubtitle = if (!owed) {
            "Turn on for a credit card bill or a loan you still have to pay"
        } else if (startingMinor > 0L) {
            "Counts as ${finance.money.format(-startingMinor)} in your total balance"
        } else {
            "Counts against your total balance"
        }

        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = StatusColorRole(
                color = scheme.primary,
                onColor = scheme.onPrimary,
                container = scheme.primaryContainer,
                onContainer = scheme.onPrimaryContainer,
            ),
            errorText = amountError,
            label = "Starting balance",
        )
        Text(
            text = "The balance before the first transaction you record here.",
            modifier = Modifier.padding(top = AppSpacing.sm),
            color = scheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(AppSpacing.sm))

        SwitchRow(
            title = "I owe this amount",
            checked = owed,
            onChange = {
                haptics.selection()
                owed = it
            },
            subtitle = owedSubtitle,
        )

        SwitchRow(
            title = "Default account",
            checked = makeDefault,
            onChange = {
                haptics.selection()
                makeDefault = it
            },
            subtitle = "Pre-selected when you add a transaction",
        )
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val clickableModifier = modifier
        .clip(AppRadius.rMd)
        .clickable(
            enabled = enabled,
            role = Role.Switch,
            onClick = { onChange(!checked) },
        )
        .padding(contentPadding)

    val colors = ListItemDefaults.colors(
        containerColor = Color.Transparent,
    )

    ListItem(
        headlineContent = { Text(title) },
        modifier = clickableModifier,
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                thumbContent = if (checked) {
                    {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    null
                },
                enabled = enabled,
            )
        },
        colors = colors,
    )
}
