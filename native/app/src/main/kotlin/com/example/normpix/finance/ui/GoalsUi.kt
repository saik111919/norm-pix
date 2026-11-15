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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Account
import com.example.normpix.finance.domain.AccountKind
import com.example.normpix.finance.domain.DEFAULT_GOAL_EMOJI
import com.example.normpix.finance.domain.FinanceConflictException
import com.example.normpix.finance.domain.FinanceValidationException
import com.example.normpix.finance.domain.GoalProgress
import com.example.normpix.finance.domain.GoalsSummary
import com.example.normpix.finance.domain.MAX_GOAL_NAME
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.SavingsGoal
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.cleanGoalName
import com.example.normpix.finance.domain.emojiIcon
import com.example.normpix.finance.domain.fault
import com.example.normpix.finance.domain.goalAccounts
import com.example.normpix.finance.domain.goalProgress
import com.example.normpix.finance.domain.goalsInOrder
import com.example.normpix.finance.domain.goalsSummary
import com.example.normpix.finance.domain.minorToInputText
import com.example.normpix.finance.domain.parseMinorUnits
import com.example.normpix.finance.domain.percentLabel
import com.example.normpix.settings.CategoryIconShape
import com.example.normpix.ui.components.AppEmptyState
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.AppLoadingState
import com.example.normpix.ui.components.BannerFigure
import com.example.normpix.ui.components.DiscardChangesGuard
import com.example.normpix.ui.components.EditorSheet
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.components.LeadingIcon
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.components.SplitStatBanner
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/*
 * Savings goals (2026-11-15): the Goals section of Plans - a banner with what is saved and what is still to go, one row a goal, and
 * the sheets that open a goal, make or change one, and add money to one that keeps its own amount. The sums are in
 * `finance/domain/Goals.kt` (pure); this only says them. A goal moves no money between accounts, and the screens say so where it counts.
 */

private val monthYear: DateTimeFormatter get() = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

private fun goals(count: Int) = if (count == 1) "goal" else "goals"

/** "₹18,000": a whole amount without its ".00", as the budget rows print what they are of. */
private fun MoneyFormatter.short(minor: Long): String = format(minor, trimZeroFraction = true)

/**
 * The line under a goal's amounts: "Reached", "The date has passed", or what a month must put by to get there in time ("₹4,400 a month to
 * reach it by Mar 2027"); null for a goal that has no date and is not reached - there is nothing to say.
 */
internal fun goalStatusLine(goal: SavingsGoal, progress: GoalProgress, money: MoneyFormatter): String? {
    val date = goal.targetDate
    val perMonth = progress.monthlyNeeded
    return when {
        progress.reached -> "Reached"
        progress.datePassed -> "The date has passed"
        perMonth != null && date != null -> "${money.short(perMonth)} a month to reach it by ${monthYear.format(date)}"
        else -> null
    }
}

/** What a goal counts, in a sentence: the account whose balance it is, or that it keeps its own amount. */
internal fun goalCountsLine(goal: SavingsGoal, account: Account?): String = when {
    goal.accountId == null -> "Saved on the goal itself. This doesn't move money between accounts."
    account == null -> "Counts the balance of an account."
    account.archived -> "Counts the balance of ${account.name} (archived), as of today."
    else -> "Counts the balance of ${account.name}, as of today."
}

/** A goal's picture: its emoji on a tonal disc in the shape the user chose for avatars, the colour kept by the goal's place so a list tells them apart. */
@Composable
fun GoalAvatar(goal: SavingsGoal, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val colors = paletteColor(((goal.id ?: goal.sortOrder.toLong()) % paletteSize).toInt())
    val glyph = size * if (LocalCategoryIconShape.current == CategoryIconShape.None) BARE_ICON_IN_BOX else ICON_IN_DISC
    // Decorative: the goal's name is what a screen reader says.
    AvatarDisc(colors, modifier.clearAndSetSemantics {}, size) { CategoryGlyph(emojiIcon(goal.symbol), colors.onContainer, glyph) }
}

// -------------------------------------------------------------------------------------------------------------- the section

/**
 * Plans > Goals: what is saved and still to go across the goals, then a row for each. A goal opens its sheet. With none, an empty state
 * that says what a goal is and offers to add one.
 */
@Composable
fun GoalsView() {
    val finance = rememberFinanceState()
    val actions = rememberFinanceActions()
    val today = LocalDate.now()
    Column(Modifier.padding(AppSpacing.page)) {
        when {
            finance.status == FinanceStatus.Loading -> AppLoadingState(label = "Loading goals")
            finance.goals.isEmpty() -> AppEmptyState(
                icon = Icons.Outlined.Savings,
                title = "No goals yet",
                message = "Save for a trip, a gadget or a rainy day. A goal can count an account's balance, or keep an amount of its own. It never moves money between your accounts.",
                actionLabel = "Add goal",
                actionIcon = Icons.Rounded.Add,
                onAction = actions::addGoal,
            )
            else -> {
                GoalsBanner(goalsSummary(finance.goals, finance.balances), finance.money)
                Spacer(Modifier.height(AppSpacing.lg))
                ListSection(title = "Goals", footer = "A goal never moves money between your accounts.") {
                    for (goal in goalsInOrder(finance.goals, finance.balances)) {
                        item { GoalRow(goal, finance, today, onClick = { actions.openGoal(goal) }) }
                    }
                }
                ListSection(modifier = Modifier.padding(top = AppSpacing.lg)) {
                    item {
                        ListRow(
                            headline = "Add goal",
                            leading = { LeadingIcon(Icons.Rounded.Add) },
                            onClick = actions::addGoal,
                        )
                    }
                }
            }
        }
    }
}

/** The two-panel banner at the top of Goals: what is saved towards the goals ("Saved") and what is still to go. */
@Composable
internal fun GoalsBanner(summary: GoalsSummary, money: MoneyFormatter, modifier: Modifier = Modifier) {
    val saved = money.short(summary.saved)
    val left = money.short(summary.left)
    val target = money.short(summary.target)
    SplitStatBanner(
        first = BannerFigure(title = "Saved", figure = saved, note = "across ${summary.count} ${goals(summary.count)}"),
        second = BannerFigure(
            title = "Still to go",
            figure = left,
            note = if (summary.reached > 0) "${summary.reached} reached" else "of $target in all",
        ),
        spoken = "Saved $saved of $target across ${summary.count} ${goals(summary.count)}, $left still to go" +
            if (summary.reached > 0) ", ${summary.reached} reached." else ".",
        modifier = modifier,
    )
}

/** One goal as a row: its picture and name, how far along it is as a bar, what is saved of the target, and what is needed or that it is reached. */
@Composable
internal fun GoalRow(goal: SavingsGoal, finance: FinanceState, today: LocalDate, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val money = finance.money
    val progress = goalProgress(goal, finance.balances, today, finance.currency.minorPerMajor)
    val status = goalStatusLine(goal, progress, money)
    val percent = percentLabel(progress.fraction)
    val amounts = "${money.short(progress.saved)} of ${money.short(progress.target)}"
    val spoken = listOfNotNull(goal.name, amounts, percent, status).joinToString(", ")
    val bar = if (progress.reached) StatusTone.Success.resolve().color else scheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open this goal", role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = spoken
                role = Role.Button
                semanticsOnClick(label = "Open this goal") {
                    onClick()
                    true
                }
            }
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
    ) {
        GoalAvatar(goal)
        Spacer(Modifier.width(AppSpacing.lg))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    goal.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    percent,
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = scheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(AppSpacing.sm))
            AppLinearProgress(
                progress = { progress.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                thickness = 8.dp,
                color = bar,
                trackColor = scheme.surfaceContainerHighest,
                calm = true,
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                amounts,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = scheme.onSurfaceVariant,
            )
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // "Reached" is said in words and by this tick, not by the bar's colour alone.
                    if (progress.reached) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = bar, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(AppSpacing.xs))
                    }
                    Text(
                        status,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.reached) bar else scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------------------ a goal's sheet

/**
 * A goal opened: its picture and name, a bar and what is saved of the target, what is left and by when, what it counts - then what can be
 * done with it, returned by key: `add` and `take` (only a goal on its own: its amount is changed here), `edit` and `delete`.
 */
@Composable
fun GoalSheet(goal: SavingsGoal, onPick: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val finance = rememberFinanceState()
    val money = finance.money
    val today = LocalDate.now()
    val live = finance.goal(goal.id) ?: goal
    val progress = goalProgress(live, finance.balances, today, finance.currency.minorPerMajor)
    val status = goalStatusLine(live, progress, money)
    val bar = if (progress.reached) StatusTone.Success.resolve().color else scheme.primary
    val actions = buildList {
        if (!live.isLinked) {
            add(SheetAction("add", Icons.Outlined.Add, "Add money"))
            if (progress.saved > 0) add(SheetAction("take", Icons.Outlined.Remove, "Take out"))
        }
        add(SheetAction("edit", Icons.Outlined.Edit, "Edit"))
        add(SheetAction("delete", Icons.Outlined.DeleteOutline, "Delete"))
    }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Column(Modifier.padding(start = AppSpacing.xxl, end = AppSpacing.xxl, bottom = AppSpacing.sm)) {
            SheetHeader(
                title = live.name,
                subtitle = "${money.short(progress.saved)} of ${money.short(progress.target)} · ${percentLabel(progress.fraction)}",
                trailing = { GoalAvatar(live, size = 48.dp) },
            )
            Spacer(Modifier.height(AppSpacing.lg))
            AppLinearProgress(
                progress = { progress.fraction.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                thickness = 8.dp,
                color = bar,
                trackColor = scheme.surfaceContainerHighest,
                calm = true,
            )
            Spacer(Modifier.height(AppSpacing.md))
            if (progress.left > 0) {
                Text("${money.short(progress.left)} left to save", style = MaterialTheme.typography.bodyLarge)
            }
            live.targetDate?.let { date ->
                Text(
                    "Target date ${shortDate(date.atStartOfDay())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
            if (status != null) {
                Text(status, style = MaterialTheme.typography.bodyMedium, color = if (progress.reached) bar else scheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                goalCountsLine(live, finance.account(live.accountId)),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
        }
        for (action in actions) {
            ListItem(
                modifier = Modifier.clickable { onPick(action.key) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(action.icon, contentDescription = null) },
                headlineContent = { Text(action.label) },
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
    }
}

// -------------------------------------------------------------------------------------------------------------------- the form

/**
 * Add or edit a goal, as a bottom sheet: a name, the target, a target date (optional), what counts as saved - an account's balance or an
 * amount the goal keeps itself - and an emoji. Calls [onDone] with the saved goal, or null when closed without saving.
 */
@Composable
fun GoalFormSheet(existing: SavingsGoal?, onDone: (SavingsGoal?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val decimals = finance.currency.decimals
    val today = LocalDate.now()

    // The accounts a goal can count; the one this goal counts already is listed whatever it has become since (archived, or a card now).
    val linkable = remember(finance.accounts, existing) {
        goalAccounts(finance.accounts) + listOfNotNull(finance.account(existing?.accountId)?.takeIf { held -> goalAccounts(finance.accounts).none { it.id == held.id } })
    }
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var target by remember { mutableStateOf(existing?.target?.let { minorToInputText(it, decimals) }.orEmpty()) }
    var date by remember { mutableStateOf(existing?.targetDate) }
    // A new goal counts a savings account's balance when there is one to count (no second set of books); otherwise it keeps its own amount.
    var linked by remember { mutableStateOf(existing?.isLinked ?: linkable.any { it.kind == AccountKind.Savings }) }
    var accountId by remember {
        mutableStateOf(existing?.accountId ?: linkable.firstOrNull { it.kind == AccountKind.Savings }?.id)
    }
    var saved by remember { mutableStateOf(existing?.takeIf { !it.isLinked && it.saved > 0 }?.let { minorToInputText(it.saved, decimals) }.orEmpty()) }
    var emoji by remember { mutableStateOf(existing?.emoji ?: DEFAULT_GOAL_EMOJI) }
    var pickingDate by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var accountError by remember { mutableStateOf<String?>(null) }
    var savedError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    fun snapshot() = listOf(name, target, date, linked, accountId, saved, emoji).joinToString("|")
    val initialSnapshot = remember { snapshot() }
    DiscardChangesGuard(dirty = snapshot() != initialSnapshot, saving = saving, isEditing = existing != null, keptMessage = "The goal stays as it was.")

    val now = remember { LocalDateTime.now() }
    val preview = SavingsGoal(id = existing?.id, name = name, target = 1, emoji = emoji, createdAt = now, updatedAt = now, sortOrder = existing?.sortOrder ?: finance.goals.size)

    fun save() {
        formError = null
        val cleanName = cleanGoalName(name)
        nameError = when {
            cleanName.isEmpty() -> "Give the goal a name"
            else -> SavingsGoal(name = cleanName, target = 1, createdAt = now, updatedAt = now).fault()?.advice
        }
        val parsedTarget = parseMinorUnits(target, decimals)
        targetError = when {
            target.isBlank() -> "Enter the amount you want to save"
            parsedTarget == null -> "Enter a valid amount"
            parsedTarget <= 0L -> "Enter a target above zero"
            else -> null
        }
        val parsedSaved = if (saved.isBlank()) 0L else parseMinorUnits(saved, decimals)
        savedError = if (!linked && parsedSaved == null) "Enter a valid amount" else null
        accountError = if (linked && accountId == null) "Choose an account" else null
        if (nameError != null || targetError != null || savedError != null || accountError != null || parsedTarget == null) return
        val draft = (existing ?: SavingsGoal(name = cleanName, target = parsedTarget, createdAt = now, updatedAt = now)).copy(
            name = cleanName,
            target = parsedTarget,
            targetDate = date,
            emoji = emoji,
            accountId = accountId.takeIf { linked },
            saved = if (linked) 0L else parsedSaved ?: 0L,
        )
        saving = true
        scope.launch {
            try {
                val result = controller.saveGoal(draft)
                haptics.success()
                onDone(result)
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = if (existing == null) "New goal" else "Edit goal",
        subtitle = "Something to save for. It only counts - it never moves money.",
        saveLabel = if (existing == null) "Save goal" else "Save changes",
        onSave = ::save,
        saving = saving,
        errorText = formError,
        titleTrailing = { GoalAvatar(preview) },
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it.take(MAX_GOAL_NAME + 10)
                nameError = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            placeholder = { Text("e.g. Trip to Goa, Emergency fund") },
            isError = nameError != null,
            supportingText = { Text(nameError ?: "${cleanGoalName(name).length}/$MAX_GOAL_NAME") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            colors = typedFieldColors(),
            shape = TonalFieldShape,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        HeroAmountField(
            value = target,
            onValueChange = {
                target = it
                targetError = null
            },
            currency = finance.currency,
            accent = TransactionType.Income.role(),
            label = "Target",
            errorText = targetError,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(AppSpacing.xl))
        TonalPickerField(
            label = "Target date (optional)",
            value = date?.let { shortDate(it.atStartOfDay()) } ?: "No date",
            icon = Icons.Outlined.Event,
            onClick = { pickingDate = true },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (date == null) "With a date, NormPix says what to put by each month." else "NormPix says what to put by each month to reach it by then.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = AppSpacing.lg, top = AppSpacing.xs),
            )
            if (date != null) TextButton(onClick = { date = null }) { Text("Remove date") }
        }
        Spacer(Modifier.height(AppSpacing.xl))
        FormGroupLabel("What counts as saved")
        ConnectedChoiceRow(
            options = remember {
                listOf(
                    ChoiceOption(true, "Linked account", Icons.Outlined.AccountBalance),
                    ChoiceOption(false, "On its own", Icons.Outlined.Savings),
                )
            },
            selected = linked,
            onSelect = {
                linked = it
                accountError = null
                savedError = null
            },
        )
        Spacer(Modifier.height(AppSpacing.md))
        if (linked) {
            AccountSelector(
                label = "Account",
                accounts = linkable,
                selected = accountId,
                onSelect = {
                    accountId = it
                    accountError = null
                },
                errorText = accountError,
            )
            Text(
                "The account's balance, as of today, counts as saved. Nothing else to keep up.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.lg, top = AppSpacing.xs),
            )
        } else {
            OutlinedTextField(
                value = saved,
                onValueChange = { entered ->
                    saved = entered.filter { it.isDigit() || it == '.' }
                    savedError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Saved so far") },
                prefix = { Text(finance.currency.symbol) },
                isError = savedError != null,
                supportingText = { Text(savedError ?: "Add money and Take out change this. This doesn't move money between accounts.") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                singleLine = true,
                colors = typedFieldColors(),
                shape = TonalFieldShape,
            )
        }
        Spacer(Modifier.height(AppSpacing.xl))
        FormGroupLabel("Emoji")
        EmojiPicker(selected = emoji, onSelect = { emoji = it })
    }

    if (pickingDate) {
        DatePickerDialogFor(
            initial = maxOf(date ?: today.plusMonths(6), today),
            onPicked = {
                pickingDate = false
                date = it
            },
            onDismiss = { pickingDate = false },
            latest = today.plusYears(MAX_GOAL_YEARS),
            earliest = today,
        )
    }
}

/** How far ahead a goal's date can be picked. */
private const val MAX_GOAL_YEARS = 30L

// --------------------------------------------------------------------------------------------------------- add money, take out

/** What Add money or Take out did: the goal as it is now, how much was moved and which way. */
class GoalMoneyResult(val goal: SavingsGoal, val amount: Long, val adding: Boolean)

/**
 * Add money to a goal that keeps its own amount, or take some out: an amount, and the way it goes. It makes no entry in the ledger -
 * the sheet says so. Taking out more than is saved is refused under the amount, in the sheet.
 */
@Composable
fun GoalMoneySheet(goal: SavingsGoal, adding: Boolean, onDone: (GoalMoneyResult?) -> Unit) {
    val controller = LocalFinance.current
    val finance = rememberFinanceState()
    val haptics = rememberAppHaptics()
    val scope = rememberCoroutineScope()
    val money = finance.money
    val decimals = finance.currency.decimals
    var amount by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    DiscardChangesGuard(dirty = amount.isNotBlank(), saving = saving, isEditing = false, keptMessage = "")

    fun save() {
        formError = null
        val parsed = parseMinorUnits(amount, decimals)
        amountError = when {
            amount.isBlank() -> "Enter an amount"
            parsed == null -> "Enter a valid amount"
            parsed <= 0L -> "Enter an amount above zero"
            !adding && parsed > goal.saved -> "You only have ${money.short(goal.saved)} saved here"
            else -> null
        }
        if (amountError != null || parsed == null) return
        saving = true
        scope.launch {
            try {
                val updated = controller.adjustGoalSaved(goal.id!!, if (adding) parsed else -parsed)
                haptics.success()
                onDone(GoalMoneyResult(updated, parsed, adding))
            } catch (e: FinanceValidationException) {
                formError = e.message
            } catch (e: FinanceConflictException) {
                formError = e.message
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                formError = "Couldn't save. Your other data is unchanged."
            } finally {
                saving = false
            }
        }
    }

    EditorSheet(
        title = if (adding) "Add money" else "Take out",
        subtitle = "${goal.name} · ${money.short(goal.saved)} saved",
        saveLabel = if (adding) "Add money" else "Take out",
        onSave = ::save,
        saving = saving,
        errorText = formError,
        titleTrailing = { GoalAvatar(goal) },
    ) {
        HeroAmountField(
            value = amount,
            onValueChange = {
                amount = it
                amountError = null
            },
            currency = finance.currency,
            accent = (if (adding) TransactionType.Income else TransactionType.Expense).role(),
            label = if (adding) "Amount to add" else "Amount to take out",
            errorText = amountError,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            "This doesn't move money between accounts. It only changes what is counted on this goal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
