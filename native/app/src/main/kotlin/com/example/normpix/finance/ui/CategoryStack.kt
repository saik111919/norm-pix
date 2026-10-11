package com.example.normpix.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.normpix.finance.data.TransactionQuery
import com.example.normpix.finance.domain.BudgetHealth
import com.example.normpix.finance.domain.BudgetStatus
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.MonthKey
import com.example.normpix.finance.domain.TransactionType
import com.example.normpix.finance.domain.relativeChange
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.DeckStack
import com.example.normpix.ui.components.ListRow
import com.example.normpix.ui.components.ListSection
import com.example.normpix.ui.components.SheetHeader
import com.example.normpix.ui.theme.AppRadius
import com.example.normpix.ui.theme.AppSpacing
import com.example.normpix.ui.theme.resolve
import kotlin.math.abs
import kotlin.math.roundToInt

/** One category's spending in a month, as Home's stack and its sheet show it. */
@Immutable
class CategorySpend(
    val categoryId: Long,
    /** Null when the category has since been deleted; its spending still counts. */
    val category: Category?,
    val month: MonthKey,
    val amount: Long,
    val count: Int,
    /** Its share of the month's spending, 0..1. */
    val share: Double,
    /** What it came to the month before (0 if it had nothing then). */
    val previous: Long,
    val budget: BudgetStatus?,
) {
    /** How much more (+) or less (-) than the month before, as a fraction; null without anything to compare to. */
    val change: Double? get() = relativeChange(amount, previous)

    val name: String get() = category?.name ?: "Deleted category"
}

/** A category card is this much wider than tall. */
private const val SPEND_ASPECT = 1.9f

/** The text on a category card grows with the system font only this far: the card keeps its proportions. */
private const val MAX_FONT_SCALE = 1.15f

/**
 * The month's biggest spending categories as a stack - the same one the cards use ([DeckStack]): the biggest in front in
 * full, the next ones behind it, each in its own colour. Tapping the front one calls [onOpen].
 */
@Composable
fun CategoryStack(spends: List<CategorySpend>, onOpen: (CategorySpend) -> Unit, modifier: Modifier = Modifier) {
    val money = rememberFinanceState().money
    val density = LocalDensity.current
    val capped = remember(density) { Density(density.density, fontScale = minOf(density.fontScale, MAX_FONT_SCALE)) }
    DeckStack(
        items = spends,
        itemKey = { it.categoryId },
        shape = AppRadius.rLg,
        description = { spend, index, count -> spend.spoken(money, index, count) },
        onOpen = onOpen,
        modifier = modifier,
        plate = { spend -> SpendSurface(spendTone(spend.category)) {} },
        face = { spend -> CompositionLocalProvider(LocalDensity provides capped) { SpendFace(spend) } },
    )
}

private fun CategorySpend.spoken(money: MoneyFormatter, index: Int, count: Int): String = buildString {
    append("$name, ${money.format(amount)} spent in ${monthLabel(month)}, ${(share * 100).roundToInt()}% of spending")
    spendingComparison(change, month.previous)?.let { append(", $it") }
    if (count > 1) append(", ${index + 1} of $count")
}

@Composable
private fun spendTone(category: Category?): PaletteColors {
    val scheme = MaterialTheme.colorScheme
    return if (category == null) PaletteColors(scheme.outline, scheme.surfaceContainerHighest, scheme.onSurfaceVariant) else paletteColor(category.color)
}

/** The card itself: the category's own tonal colour, lit from its top right, with a hairline edge. */
@Composable
private fun SpendSurface(tone: PaletteColors, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(SPEND_ASPECT)
            .clip(AppRadius.rLg)
            .background(tone.container)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to tone.accent.copy(alpha = 0.42f),
                        1f to Color.Transparent,
                        center = Offset(size.width * 0.92f, -size.height * 0.05f),
                        radius = size.width * 0.75f,
                    ),
                )
                drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.1f)))
            }
            .drawWithContent {
                drawContent()
                // Twice as wide as it shows: the clip takes the half that is outside.
                drawOutline(AppRadius.rLg.createOutline(size, layoutDirection, this), tone.onContainer.copy(alpha = 0.14f), style = Stroke(2.dp.toPx()))
            }
            .padding(20.dp),
    ) { content() }
}

@Composable
private fun SpendFace(spend: CategorySpend) {
    val money = rememberFinanceState().money
    val tone = spendTone(spend.category)
    val type = MaterialTheme.typography
    val ink = tone.onContainer
    SpendSurface(tone) {
        Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpendAvatar(spend.category, tone)
                Spacer(Modifier.width(12.dp))
                Text(
                    spend.name,
                    style = type.titleMedium,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                spend.change?.let { ChangePill(it, ink) }
            }
            Spacer(Modifier.weight(1f))
            Text(
                money.format(spend.amount),
                style = type.headlineLarge.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
                color = ink,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = type.headlineLarge.fontSize),
            )
            Text(
                "${spend.count} ${if (spend.count == 1) "transaction" else "transactions"} in ${monthLabel(spend.month)}",
                style = type.bodyMedium,
                color = ink.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppLinearProgress(
                    progress = { spend.share.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    thickness = 6.dp,
                    color = tone.accent,
                    trackColor = ink.copy(alpha = 0.14f),
                    calm = true,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "${(spend.share * 100).roundToInt()}%",
                    style = type.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES),
                    color = ink,
                )
            }
        }
    }
}

/** The category's icon on a soft disc of its own ink - on the card the tonal avatar would vanish into the card's colour. */
@Composable
private fun SpendAvatar(category: Category?, tone: PaletteColors) {
    Box(Modifier.size(40.dp).background(tone.onContainer.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        if (category == null) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, tint = tone.onContainer, modifier = Modifier.size(22.dp))
        } else {
            CategoryGlyph(category.icon, tone.onContainer, 22.dp)
        }
    }
}

/** "▲ 18%": whether it was more or less than the month before - a fact, not a verdict, so no red or green. */
@Composable
private fun ChangePill(change: Double, ink: Color) {
    val percent = (abs(change) * 100).roundToInt()
    if (percent == 0) return
    Row(
        Modifier
            .clip(AppRadius.rFull)
            .background(ink.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (change > 0) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(2.dp))
        Text("$percent%", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TABULAR_FIGURES), color = ink)
    }
}

/**
 * One category's month in a sheet: what it came to, how that compares to the month before and to everything spent, its
 * budget if it has one, and its latest transactions. [onSeeAll] is for "see all of them": the sheet sits above the
 * navigation graph, so the caller closes it and does the navigating.
 */
@Composable
fun CategorySpendSheet(spend: CategorySpend, onSeeAll: () -> Unit) {
    val finance = rememberFinanceState()
    val money = finance.money
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val latest by rememberFinanceQuery(spend.categoryId to spend.month) {
        transactions(
            TransactionQuery(
                range = spend.month.range,
                types = setOf(TransactionType.Expense),
                categoryIds = setOf(spend.categoryId),
            ),
            limit = LATEST,
        ).items
    }
    // A category with sub-categories shows where its total went: straight to it, or on to which sub-category.
    val subCategories = finance.categories.filter { it.parentId == spend.categoryId }
    val monthTotals by rememberFinanceQuery(spend.categoryId to spend.month) {
        if (subCategories.isEmpty()) emptyList() else categoryTotals(spend.month.range, TransactionType.Expense)
    }
    val comparison = spendingComparison(spend.change, spend.month.previous)

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = AppSpacing.xxl, top = AppSpacing.xs, end = AppSpacing.xxl, bottom = AppSpacing.xl),
    ) {
        SheetHeader(title = spend.name, subtitle = "Spent in ${monthLabel(spend.month)}")
        Spacer(Modifier.height(AppSpacing.lg))
        Text(
            money.format(spend.amount),
            style = type.displaySmall.copy(fontFeatureSettings = TABULAR_FIGURES, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(minFontSize = 24.sp, maxFontSize = type.displaySmall.fontSize),
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            "${(spend.share * 100).roundToInt()}% of everything you spent · ${spend.count} ${if (spend.count == 1) "transaction" else "transactions"}",
            style = type.bodyMedium,
            color = scheme.onSurfaceVariant,
        )
        if (comparison != null) {
            Text(comparison, style = type.bodyMedium, color = scheme.onSurfaceVariant)
        }
        spend.budget?.let { budget ->
            Spacer(Modifier.height(AppSpacing.xl))
            SpendBudget(budget, money)
        }
        if (subCategories.isNotEmpty()) {
            val byId = (monthTotals as? AsyncValue.Data)?.value.orEmpty().associateBy { it.categoryId }
            val own = byId[spend.categoryId]
            val inSubs = subCategories.mapNotNull { sub -> byId[sub.id]?.let { sub to it } }.sortedByDescending { it.second.amount }
            if (own != null || inSubs.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.xl))
                ListSection(title = "By sub-category") {
                    for ((sub, total) in inSubs) {
                        item {
                            ListRow(
                                headline = sub.name,
                                supporting = "${total.count} ${if (total.count == 1) "transaction" else "transactions"}",
                                leading = { CategoryAvatar(sub, size = 32.dp) },
                                trailing = { Text(money.format(total.amount), style = type.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES)) },
                            )
                        }
                    }
                    if (own != null) {
                        item {
                            ListRow(
                                headline = "Directly in ${spend.name}",
                                supporting = "${own.count} ${if (own.count == 1) "transaction" else "transactions"}",
                                leading = { CategoryAvatar(finance.category(spend.categoryId), size = 32.dp) },
                                trailing = { Text(money.format(own.amount), style = type.titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES)) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.xl))
        when (val value = latest) {
            is AsyncValue.Data -> if (value.value.isNotEmpty()) {
                ListSection(title = "Latest") {
                    for (transaction in value.value) item { TransactionTile(transaction, showDate = true) }
                }
            }
            else -> Unit
        }
        Spacer(Modifier.height(AppSpacing.lg))
        Button(onClick = onSeeAll, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text("See all in ${monthLabel(spend.month)}")
        }
    }
}

/** How many of a category's latest transactions the sheet lists. */
private const val LATEST = 5

/** The category's budget as a bar with what is left; words carry the state, colour only reinforces it. */
@Composable
private fun SpendBudget(budget: BudgetStatus, money: MoneyFormatter) {
    val scheme = MaterialTheme.colorScheme
    val tone = if (budget.health == BudgetHealth.Healthy) scheme.primary else budgetTone(budget.health).resolve().color
    val percent = (budget.utilization * 100).roundToInt()
    val words = if (budget.health == BudgetHealth.Exceeded) {
        "${money.format(-budget.remaining)} over a budget of ${money.format(budget.allocated, trimZeroFraction = true)}"
    } else {
        "${money.format(budget.remaining)} left of ${money.format(budget.allocated, trimZeroFraction = true)}"
    }
    Column(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Budget: $words, $percent% used" }) {
        Text("Budget", style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
        Spacer(Modifier.height(AppSpacing.sm))
        AppLinearProgress(
            progress = { budget.utilization.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            thickness = 10.dp,
            color = tone,
            trackColor = scheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(words, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
    }
}
