package com.example.normpix.finance.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.normpix.finance.domain.FrequentEntry
import com.example.normpix.ui.theme.AppSpacing

/** The test tag of the "Frequent" row in the new-transaction form. */
internal const val FREQUENT_ROW_TAG = "frequent_entries"

/**
 * "Frequent" at the top of a new expense or income: what the user enters again and again ([com.example.normpix.finance.domain.frequentEntries]),
 * one chip each - the payee's logo or the category's icon, the payee (or, with none, the category) and the amount it had last time. A
 * tap fills the category, the payee and the account in, and the amount too while none is typed; everything stays editable. The row
 * scrolls sideways when the chips don't fit. Nothing shows when there is nothing frequent yet.
 */
@Composable
internal fun FrequentEntriesRow(entries: List<FrequentEntry>, onPick: (FrequentEntry) -> Unit, modifier: Modifier = Modifier) {
    val finance = rememberFinanceState()
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().testTag(FREQUENT_ROW_TAG)) {
        Text("Frequent", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.sm))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        ) {
            for (entry in entries) {
                val category = finance.category(entry.categoryId)
                val title = entry.payee ?: category?.name.orEmpty()
                val amount = finance.money.format(entry.amount, trimZeroFraction = true)
                Surface(
                    onClick = { onPick(entry) },
                    shape = CircleShape,
                    color = scheme.surfaceContainerHighest,
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .clearAndSetSemantics {
                            contentDescription = listOfNotNull(title, amount, category?.name?.takeIf { entry.payee != null }).joinToString(", ")
                            role = Role.Button
                            onClick(label = "Fill in") { onPick(entry); true }
                        },
                ) {
                    Row(
                        Modifier.padding(start = AppSpacing.xs + 2.dp, end = AppSpacing.lg, top = AppSpacing.xs, bottom = AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryAvatar(category, size = 36.dp, payee = entry.payee)
                        Spacer(Modifier.width(AppSpacing.sm))
                        Column(Modifier.widthIn(max = 160.dp)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelLarge,
                                color = scheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                amount,
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = TABULAR_FIGURES),
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
