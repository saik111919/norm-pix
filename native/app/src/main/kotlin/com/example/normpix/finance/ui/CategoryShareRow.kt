package com.example.normpix.finance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.example.normpix.ui.components.AppLinearProgress
import com.example.normpix.ui.theme.AppSpacing
import kotlin.math.roundToInt

/**
 * One category's share of the month's spending, as a row of a ListSection - what Home's Top spending and Insights'
 * Where your money went both show.
 */
@Composable
fun CategoryShareRow(
    category: Category?,
    amount: Long,
    share: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    val percent = "${(share * 100).roundToInt()}%"
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
            Text(
                finance.money.format(amount),
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = TABULAR_FIGURES),
            )
        },
    )
}
