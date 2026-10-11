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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.example.normpix.navigation.LocalAppPreferences
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.DialogBackdropBlur
import com.example.normpix.ui.components.rememberDialogSurface

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.CurrencySpec
import com.example.normpix.finance.domain.FinanceTransaction
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.pathName
import com.example.normpix.ui.components.rememberAppHaptics
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.StatusTone
import com.example.normpix.ui.theme.resolve
import kotlin.math.roundToInt

/**
 * "< September 2026 >" - steps between months; tapping the label opens a
 * month picker. [latest] caps forward navigation. [trailing] sits after the
 * arrows (the history's way out of the month into everything).
 */
@Composable
fun MonthSwitcher(
    month: MonthKey,
    latest: MonthKey,
    onChanged: (MonthKey) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberAppHaptics()
    var picking by remember { mutableStateOf(false) }
    val go = { next: MonthKey ->
        haptics.selection()
        onChanged(next)
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { go(month.previous) }) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
        }
        TextButton(
            onClick = { picking = true },
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "${monthLabelFull(month)}, choose month" },
        ) {
            Text(monthLabel(month), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
        }
        IconButton(onClick = { go(month.next) }, enabled = month.isBefore(latest)) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
        }
        trailing?.invoke()
    }
    if (picking) {
        MonthPickerDialog(
            initial = month,
            latest = latest,
            onPicked = { picked ->
                picking = false
                if (picked != month) go(picked)
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthPickerDialog(initial: MonthKey, latest: MonthKey, onPicked: (MonthKey) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableIntStateOf(initial.year) }
    val preferences = LocalAppPreferences.current
    val prefsState = preferences?.state?.collectAsState()
    val sheetBlurEnabled = prefsState?.value?.sheetBlurEnabled ?: true
    val surface = rememberDialogSurface(enabled = sheetBlurEnabled)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface.container,
        shape = surface.shape,
        title = {
            DialogBackdropBlur(surface.blurBehind)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { year-- }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous year") }
                Text("$year", Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { year++ }, enabled = year < latest.year) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next year")
                }
            }
        },
        text = {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            ) {
                for (m in 1..12) {
                    val key = MonthKey(year, m)
                    val enabled = !key.isAfter(latest)
                    if (key == initial) {
                        Button(onClick = { onPicked(key) }, modifier = Modifier.width(88.dp)) { Text(monthShort(key)) }
                    } else {
                        TextButton(onClick = { onPicked(key) }, enabled = enabled, modifier = Modifier.width(88.dp)) {
                            Text(monthShort(key))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * One transaction in a list: what (category or payee), where (account), and
 * how much - with a single, explicit phrase for screen readers.
 */
@Composable
fun TransactionTile(
    transaction: FinanceTransaction,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDate: Boolean = false,
    /** The account whose list this row is in: its transfers are shown as money in (+) or out (-) of it. */
    perspectiveAccountId: Long? = null,
) {
    val finance = rememberFinanceState()
    // At large text a row gets room for a second line of its title and its details, instead of cutting them short.
    val roomy = LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE
    val t = transaction
    val direction = transferDirection(t, perspectiveAccountId)
    val category = finance.category(t.categoryId)
    val person = finance.person(t.personId)
    val account = finance.account(t.accountId)
    val to = finance.account(t.toAccountId)
    val isTransfer = t.type == TransactionType.Transfer

    val title = t.payee ?: if (isTransfer) "Transfer" else category?.name ?: "Uncategorized"
    val details = buildList {
        if (isTransfer) {
            add("${account?.name ?: "Unknown"} → ${to?.name ?: "Unknown"}")
        } else {
            if (t.payee != null && category != null) add(finance.categories.pathName(category))
            add(account?.name ?: "Unknown account")
            person?.let { add("For ${it.name}") }
        }
    }
    // The date is shown under the amount, but a screen reader still hears it where it always was: after the account.
    val spokenDetails = if (showDate) details + shortDate(t.occurredAt) else details
    val amountText = finance.money.format(t.amount)
    val spoken = "${direction?.label ?: t.type.label}, $amountText, $title, ${spokenDetails.joinToString(", ")}" +
        if (t.recurringId != null) ", recurring" else ""

    ListItem(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = spoken
                if (onClick != null) {
                    role = Role.Button
                    onClick { onClick(); true }
                }
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { if (isTransfer) TransferAvatar() else CategoryAvatar(category) },
        headlineContent = { Text(title, maxLines = if (roomy) 2 else 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.recurringId != null) {
                    Icon(Icons.Rounded.Repeat, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(AppSpacing.xs))
                }
                Text(details.joinToString(" · "), maxLines = if (roomy) 2 else 1, overflow = TextOverflow.Ellipsis)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                TransactionAmount(t.type, t.amount, finance.money, style = MaterialTheme.typography.titleSmall, direction = direction)
                if (showDate) {
                    Text(
                        shortDate(t.occurredAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

private val amountShape = Regex("^\\d{0,12}(\\.\\d*)?$")

/**
 * Accepts only a non-negative decimal with at most [decimals] fraction digits
 * (the value is later parsed exactly by `parseMinorUnits`). Returns the text
 * to keep: [new] if valid, otherwise [old].
 */
fun filterAmountInput(old: String, new: String, decimals: Int): String {
    val text = new.replace(",", "")
    if (!amountShape.matches(text)) return old
    val fraction = text.substringAfter('.', "")
    if (text.contains('.') && (decimals == 0 || fraction.length > decimals)) return old
    return text
}

/**
 * A keyboard-friendly amount field for the ledger's currency: digits are grouped as they are typed ("12,50,000"), and the
 * symbol sits in front. [helpText] shows under it until there is an [errorText] to show instead.
 */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    currency: CurrencySpec,
    modifier: Modifier = Modifier,
    label: String = "Amount",
    large: Boolean = false,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    helpText: String? = null,
) {
    val style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.bodyLarge
    val grouping = remember(currency) { AmountTransformation(prefix = "", indianGrouping = currency.indianGrouping) }
    val supporting = errorText ?: helpText
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(filterAmountInput(value, it, currency.decimals)) },
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        prefix = { Text(currency.symbol.trim(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
        placeholder = { Text(if (currency.decimals == 0) "0" else "0.00") },
        textStyle = style.copy(fontFeatureSettings = TABULAR_FIGURES),
        visualTransformation = grouping,
        singleLine = true,
        isError = errorText != null,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(
            keyboardType = if (currency.decimals > 0) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = imeAction,
        ),
        colors = typedFieldColors(),
        shape = TonalFieldShape,
    )
}

/** Large outer corners on one diagonal, small on the other: the hero is the only surface shaped like this (Home, Insights). */
val HeroShape = RoundedCornerShape(topStart = 40.dp, topEnd = 16.dp, bottomEnd = 40.dp, bottomStart = 16.dp)

/** A labelled amount in a summary row ("Spent Rs 24,500"). */
@Composable
fun SummaryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.titleLarge,
    color: Color? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = valueStyle.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = color ?: Color.Unspecified,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = valueStyle.fontSize),
        )
    }
}

/**
 * Budget progress line with its status in words and tone. All thresholds come
 * from the domain [BudgetStatus]; this only presents them.
 */
@Composable
fun BudgetProgress(
    status: BudgetStatus,
    money: MoneyFormatter,
    modifier: Modifier = Modifier,
    onContainer: Color? = null,
    /** A bar among others (a list of budgets) stays straight; the one that stands alone keeps its wave. */
    calm: Boolean = false,
) {
    val health = status.health
    val role = budgetTone(health).resolve()
    val scheme = MaterialTheme.colorScheme
    val percent = (status.utilization * 100).roundToInt()
    val over = health == BudgetHealth.Exceeded
    val message = if (over) {
        "${money.format(-status.remaining)} over · $percent% used"
    } else {
        "${money.format(status.remaining)} left · $percent% used"
    }
    val textColor = if (over) role.color else onContainer ?: scheme.onSurfaceVariant
    val spoken = "Budget ${money.format(status.allocated)}, spent ${money.format(status.spent)}, $message"

    Column(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        AppLinearProgress(
            progress = { status.utilization.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            thickness = 8.dp,
            color = role.color,
            trackColor = scheme.surfaceContainerHighest,
            calm = calm,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (health != BudgetHealth.Healthy) {
                Icon(budgetIcon(health), contentDescription = null, tint = textColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(AppSpacing.xs))
            }
            Text(
                message,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                "of ${money.format(status.allocated, trimZeroFraction = true)}",
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                color = onContainer ?: scheme.onSurfaceVariant,
            )
        }
    }
}

fun budgetTone(health: BudgetHealth): StatusTone = when (health) {
    BudgetHealth.Healthy -> StatusTone.Success
    BudgetHealth.Approaching -> StatusTone.Warning
    BudgetHealth.Exceeded -> StatusTone.Error
}

fun budgetIcon(health: BudgetHealth): ImageVector = when (health) {
    BudgetHealth.Healthy -> Icons.Outlined.CheckCircle
    BudgetHealth.Approaching -> Icons.Outlined.Info
    BudgetHealth.Exceeded -> Icons.Outlined.ErrorOutline
}

fun budgetLabel(health: BudgetHealth): String = when (health) {
    BudgetHealth.Healthy -> "On track"
    BudgetHealth.Approaching -> "Nearly used"
    BudgetHealth.Exceeded -> "Over budget"
}

/** Inline error text under a form group that isn't a text field. */
@Composable
fun FieldError(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier
            .padding(top = AppSpacing.xs)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Title above a group of form controls. */
@Composable
fun FormGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(bottom = AppSpacing.sm))
}

/** The supporting figures of a hero card: side by side, or - where large text could not fit them across - one above another. */
@Composable
fun HeroStats(stats: List<@Composable (Modifier) -> Unit>, modifier: Modifier = Modifier) {
    if (LocalDensity.current.fontScale > HERO_STATS_STACK_SCALE) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) { stats.forEach { it(Modifier.fillMaxWidth()) } }
    } else {
        // A label that wraps (large text) must not push its value off the others' line.
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.Bottom) {
            stats.forEach { it(Modifier.weight(1f)) }
        }
    }
}

/** From this font scale up, a hero card's figures stack. */
const val HERO_STATS_STACK_SCALE = 1.3f
