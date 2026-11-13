package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.Category
import com.example.normpix.finance.domain.MoneyFormatter
import com.example.normpix.finance.domain.percentLabel
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.components.Icon
import com.example.normpix.ui.theme.AppSpacing
import kotlin.math.abs

/**
 * How one category's spending stands against the period it is compared with: [delta] more (+) or less (-) than then; [isNew] when
 * there was nothing in it then. A fact, not a verdict, so it is never red or green (as on the category cards).
 */
class CategoryRowChange(val delta: Long, val isNew: Boolean) {
    /** What a screen reader hears after the row's other facts: "₹600.00 more than this time last month". */
    fun spoken(money: MoneyFormatter, basis: String): String = when {
        isNew -> "nothing in $basis"
        delta == 0L -> "the same as $basis"
        else -> "${money.format(abs(delta))} ${if (delta > 0) "more" else "less"} than $basis"
    }
}

/**
 * One category's share of the month's spending, as a row of a ListSection - what Home's Top spending and Insights'
 * Where your money went both show. [change], when there is something to compare with, is the small figure under the amount
 * (owner, 2026-11-13, after both researchers found the same categories listed twice, here and in a separate "What changed").
 */
@Composable
fun CategoryShareRow(
    category: Category?,
    amount: Long,
    share: Double,
    modifier: Modifier = Modifier,
    change: CategoryRowChange? = null,
    onClick: (() -> Unit)? = null,
) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val percent = percentLabel(share)
    val barColor = if (category == null) scheme.outline else paletteColor(category.color).accent
    ListItem(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier.semantics(mergeDescendants = true) {},
        ),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { CategoryAvatar(category) },
        headlineContent = {
            Text(category?.name ?: "Deleted category", maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Row(Modifier.padding(top = AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                AppLinearProgress(
                    progress = { share.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    thickness = 6.dp,
                    color = barColor,
                    trackColor = scheme.surfaceContainerHighest,
                    calm = true,
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(percent)
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    finance.money.format(amount),
                    style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
                )
                if (change != null) ChangeLine(change, finance.money)
            }
        },
    )
}

/** An arrow and how much: "▲ ₹600", "▼ ₹400", "New" or "Same". The arrow is the words' twin, so it is never colour alone. */
@Composable
private fun ChangeLine(change: CategoryRowChange, money: MoneyFormatter) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        val arrow = when {
            change.isNew || change.delta == 0L -> null
            change.delta > 0 -> Icons.Rounded.ArrowUpward
            else -> Icons.Rounded.ArrowDownward
        }
        if (arrow != null) {
            Icon(arrow, contentDescription = null, tint = ink, modifier = Modifier.size(AppSpacing.md))
            Spacer(Modifier.width(AppSpacing.xxs))
        }
        Text(
            when {
                change.isNew -> "New"
                change.delta == 0L -> "Same"
                else -> money.format(abs(change.delta), trimZeroFraction = true)
            },
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            color = ink,
            maxLines = 1,
        )
    }
}
